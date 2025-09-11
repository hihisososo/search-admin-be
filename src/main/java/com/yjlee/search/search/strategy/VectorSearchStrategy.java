package com.yjlee.search.search.strategy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.search.constants.SearchConstants;
import com.yjlee.search.search.converter.ProductDtoConverter;
import com.yjlee.search.search.dto.*;
import com.yjlee.search.search.dto.VectorSearchResult;
import com.yjlee.search.search.service.VectorSearchService;
import com.yjlee.search.search.utils.AggregationUtils;
import jakarta.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSearchStrategy implements SearchStrategy {

  private final VectorSearchService vectorSearchService;
  private final ProductDtoConverter productDtoConverter;
  private final ElasticsearchClient elasticsearchClient;

  @Override
  public SearchExecuteResponse search(
      String indexName, SearchExecuteRequest request, boolean withExplain) {
    log.info("Executing multi-field vector search for query: {}", request.getQuery());

    long startTime = System.currentTimeMillis();

    // 벡터 검색 설정 (필터 없이)
    VectorSearchConfig config =
        VectorSearchConfig.builder()
            .topK(
                request.getHybridTopK() != null
                    ? request.getHybridTopK()
                    : SearchConstants.DEFAULT_HYBRID_TOP_K)
            .vectorMinScore(request.getVectorMinScore())
            .nameVectorBoost(
                request.getNameVectorBoost() != null ? request.getNameVectorBoost() : 0.7f)
            .specsVectorBoost(
                request.getSpecsVectorBoost() != null ? request.getSpecsVectorBoost() : 0.3f)
            .build();

    VectorSearchResult searchResult =
        vectorSearchService.multiFieldVectorSearchWithRequest(
            indexName, request.getQuery(), config);

    SearchResponse<JsonNode> response = searchResult.getResponse();
    List<Hit<JsonNode>> allHits = response.hits().hits();

    // Post-filtering 적용
    List<Hit<JsonNode>> filteredHits = applyPostFiltering(allHits, request.getFilters());

    // 정렬 처리
    ProductSortType sortType =
        Optional.ofNullable(request.getSort())
            .map(ProductSortDto::getSortType)
            .orElse(ProductSortType.SCORE);

    SortOrder sortOrder =
        Optional.ofNullable(request.getSort())
            .map(ProductSortDto::getSortOrder)
            .map(ProductSortOrder::getSortOrder)
            .orElse(SortOrder.Desc);

    List<Hit<JsonNode>> sortedHits = applySorting(filteredHits, sortType, sortOrder);

    // 페이징 처리
    int page = request.getPage();
    int size = request.getSize();
    int from = page * size;
    int to = Math.min(from + size, sortedHits.size());

    List<Hit<JsonNode>> pagedHits =
        from < sortedHits.size() ? sortedHits.subList(from, to) : new ArrayList<>();

    // ProductDto 변환
    List<ProductDto> products =
        pagedHits.stream().map(productDtoConverter::convert).collect(Collectors.toList());

    // Response 생성
    SearchHitsDto hits =
        SearchHitsDto.builder().total((long) sortedHits.size()).data(products).build();

    int totalPages = (int) Math.ceil((double) sortedHits.size() / request.getSize());
    SearchMetaDto meta =
        SearchMetaDto.builder()
            .page(request.getPage())
            .size(request.getSize())
            .totalPages(totalPages)
            .processingTime(System.currentTimeMillis() - startTime)
            .searchSessionId(request.getSearchSessionId())
            .build();

    // 벡터 검색 결과에서 aggregation 계산
    Map<String, List<AggregationBucketDto>> aggregations =
        AggregationUtils.calculateFromHits(sortedHits);

    // SearchRequest를 JSON으로 변환
    String queryDsl = null;
    if (withExplain) {
      queryDsl = convertSearchRequestToJson(searchResult.getRequest());
    }

    return SearchExecuteResponse.builder()
        .hits(hits)
        .aggregations(aggregations)
        .meta(meta)
        .queryDsl(queryDsl)
        .build();
  }

  @Override
  public boolean supports(SearchExecuteRequest request) {
    return request.getSearchMode() == SearchMode.VECTOR_MULTI_FIELD;
  }

  private List<Hit<JsonNode>> applySorting(
      List<Hit<JsonNode>> hits, ProductSortType sortType, SortOrder sortOrder) {

    // Score 정렬은 이미 점수 순으로 되어 있으므로 그대로 반환
    if (sortType == ProductSortType.SCORE) {
      return sortOrder == SortOrder.Asc
          ? hits.stream().sorted(Comparator.comparing(Hit::score)).collect(Collectors.toList())
          : hits;
    }

    // 필드명 결정
    String fieldName = getFieldName(sortType);

    // Comparator 생성
    Comparator<Hit<JsonNode>> comparator = createComparator(fieldName, sortOrder);

    // 2차 정렬: score 내림차순
    comparator = comparator.thenComparing(Hit::score, Comparator.reverseOrder());

    return hits.stream().sorted(comparator).collect(Collectors.toList());
  }

  private String getFieldName(ProductSortType sortType) {
    switch (sortType) {
      case PRICE:
        return ESFields.PRICE;
      case RATING:
        return ESFields.RATING;
      case REVIEW_COUNT:
        return ESFields.REVIEW_COUNT;
      case REGISTERED_MONTH:
        return ESFields.REGISTERED_MONTH;
      default:
        throw new IllegalArgumentException("Unsupported sort type: " + sortType);
    }
  }

  private Comparator<Hit<JsonNode>> createComparator(String fieldName, SortOrder sortOrder) {
    // REGISTERED_MONTH는 문자열로 비교
    if (fieldName.equals(ESFields.REGISTERED_MONTH)) {
      Comparator<Hit<JsonNode>> comparator =
          Comparator.comparing(
              hit -> {
                JsonNode source = hit.source();
                if (source == null || !source.has(fieldName) || source.get(fieldName).isNull()) {
                  return sortOrder == SortOrder.Asc ? "9999-99" : "0000-00";
                }
                return source.get(fieldName).asText();
              });
      return sortOrder == SortOrder.Desc ? comparator.reversed() : comparator;
    }

    // 나머지 필드는 숫자로 비교
    Comparator<Hit<JsonNode>> comparator =
        Comparator.comparing(
            hit -> {
              JsonNode source = hit.source();
              if (source == null || !source.has(fieldName) || source.get(fieldName).isNull()) {
                return sortOrder == SortOrder.Asc ? Double.MAX_VALUE : Double.MIN_VALUE;
              }
              return source.get(fieldName).asDouble();
            });

    return sortOrder == SortOrder.Desc ? comparator.reversed() : comparator;
  }

  private String convertSearchRequestToJson(
      co.elastic.clients.elasticsearch.core.SearchRequest searchRequest) {
    if (searchRequest == null) {
      return null;
    }

    try {
      StringWriter writer = new StringWriter();
      JsonpMapper jsonpMapper = elasticsearchClient._jsonpMapper();
      JsonGenerator generator = jsonpMapper.jsonProvider().createGenerator(writer);
      searchRequest.serialize(generator, jsonpMapper);
      generator.close();
      return writer.toString();
    } catch (Exception e) {
      log.warn("SearchRequest JSON 변환 실패: {}", e.getMessage());
      return null;
    }
  }

  /** Post-filtering 적용 */
  private List<Hit<JsonNode>> applyPostFiltering(
      List<Hit<JsonNode>> hits, ProductFiltersDto filters) {
    if (filters == null) {
      return hits;
    }

    return hits.stream()
        .filter(hit -> matchesFilters(hit.source(), filters))
        .collect(Collectors.toList());
  }

  /** 필터 매칭 확인 */
  private boolean matchesFilters(JsonNode source, ProductFiltersDto filters) {
    if (source == null) {
      return false;
    }

    // 브랜드 필터
    if (filters.getBrand() != null && !filters.getBrand().isEmpty()) {
      String brandName =
          source.has(ESFields.BRAND_NAME) ? source.get(ESFields.BRAND_NAME).asText() : null;
      if (brandName == null || !filters.getBrand().contains(brandName)) {
        return false;
      }
    }

    // 카테고리 필터
    if (filters.getCategory() != null && !filters.getCategory().isEmpty()) {
      String categoryName =
          source.has(ESFields.CATEGORY_NAME) ? source.get(ESFields.CATEGORY_NAME).asText() : null;
      if (categoryName == null || !filters.getCategory().contains(categoryName)) {
        return false;
      }
    }

    // 가격 범위 필터
    if (filters.getPriceRange() != null) {
      PriceRangeDto priceRange = filters.getPriceRange();
      Double price = source.has(ESFields.PRICE) ? source.get(ESFields.PRICE).asDouble() : null;
      if (price == null) {
        return false;
      }
      if (priceRange.getFrom() != null && price < priceRange.getFrom()) {
        return false;
      }
      if (priceRange.getTo() != null && price > priceRange.getTo()) {
        return false;
      }
    }

    return true;
  }
}
