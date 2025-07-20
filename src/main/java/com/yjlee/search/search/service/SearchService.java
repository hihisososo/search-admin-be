package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.dto.*;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

  private final ElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public AutocompleteResponse getAutocompleteSuggestions(String keyword) {
    log.info("자동완성 검색 요청 - 키워드: {}", keyword);

    try {
      long startTime = System.currentTimeMillis();

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index("autocomplete")
                      .query(
                          q ->
                              q.match(
                                  m -> m.field("name_icu").operator(Operator.And).query(keyword)))
                      .size(10));

      SearchResponse<JsonNode> response = esClient.search(searchRequest, JsonNode.class);

      long took = System.currentTimeMillis() - startTime;

      List<String> suggestions = new ArrayList<>();
      for (Hit<JsonNode> hit : response.hits().hits()) {
        JsonNode source = hit.source();
        if (source != null && source.has("name")) {
          String suggestion = source.get("name").asText();
          if (!suggestions.contains(suggestion)) {
            suggestions.add(suggestion);
          }
        }
      }

      log.info("자동완성 검색 완료 - 키워드: {}, 소요시간: {}ms, 결과수: {}", keyword, took, suggestions.size());

      return AutocompleteResponse.builder()
          .suggestions(suggestions)
          .count(suggestions.size())
          .build();

    } catch (Exception e) {
      log.error("자동완성 검색 실패 - 키워드: {}", keyword, e);
      throw new RuntimeException("자동완성 검색 실패: " + e.getMessage(), e);
    }
  }

  public SearchExecuteResponse searchProducts(SearchExecuteRequest request) {
    log.info(
        "상품 검색 요청 - 검색어: {}, 페이지: {}, 크기: {}",
        request.getQuery(),
        request.getPage(),
        request.getSize());

    try {
      long startTime = System.currentTimeMillis();

      BoolQuery boolQuery = buildBoolQuery(request);
      Map<String, Aggregation> aggregations = buildAggregations(request);
      SearchRequest searchRequest = buildSearchRequest(request, boolQuery, aggregations);
      log.info("Elasticsearch 쿼리 요청 - {}", searchRequest.toString());

      SearchResponse<JsonNode> response = esClient.search(searchRequest, JsonNode.class);
      long took = System.currentTimeMillis() - startTime;

      List<ProductDto> products = extractProducts(response);
      Map<String, List<AggregationBucketDto>> aggregationResults = extractAggregations(response);

      return buildSearchResponse(request, response, products, aggregationResults, took);

    } catch (Exception e) {
      log.error("상품 검색 실패 - 검색어: {}", request.getQuery(), e);
      throw new RuntimeException("상품 검색 실패: " + e.getMessage(), e);
    }
  }

  private BoolQuery buildBoolQuery(SearchExecuteRequest request) {
    String query = TextPreprocessor.preprocess(request.getQuery());

    // 검색 결과에 영향을 주는 쿼리 (should 조건으로 OR 처리)
    List<Query> mustMatchQueries = buildMustMatchQueries(query);

    // 검색 결과에 영향 없이 추가 부스팅만 하는 쿼리
    List<Query> boostingQueries = buildBoostingQueries(query);

    // 필터 조건
    List<Query> filterQueries = buildFilterQueries(request);

    return BoolQuery.of(
        b -> {
          if (!mustMatchQueries.isEmpty()) {
            b.should(mustMatchQueries).minimumShouldMatch("1");
          }
          if (!boostingQueries.isEmpty()) {
            b.should(boostingQueries);
          }
          if (!filterQueries.isEmpty()) {
            b.filter(filterQueries);
          }
          return b;
        });
  }

  /** 검색 결과에 영향을 주는 쿼리들 구성 */
  private List<Query> buildMustMatchQueries(String query) {
    List<Query> queries = new ArrayList<>();

    // 1. name, description 크로스 필드 AND 검색 (name 가중치 > description)
    queries.add(
        Query.of(
            q ->
                q.multiMatch(
                    m ->
                        m.query(query)
                            .fields("name^3.0", "description^1.0")
                            .type(TextQueryType.CrossFields)
                            .operator(Operator.And)
                            .boost(10.0f))));

    // 2. name.brgram, description.brgram 크로스 필드 AND 검색 (name 가중치 > description)
    queries.add(
        Query.of(
            q ->
                q.multiMatch(
                    m ->
                        m.query(query)
                            .fields("name.bigram^2.0", "description.bigram^1.0")
                            .type(TextQueryType.CrossFields)
                            .operator(Operator.And)
                            .boost(6.0f))));

    return queries;
  }

  /** 검색 결과에 영향 없이 추가 부스팅만 하는 쿼리들 구성 */
  private List<Query> buildBoostingQueries(String query) {
    List<Query> queries = new ArrayList<>();
    String[] queryWords = query.split("\\s+");

    // 3. 검색어를 공백으로 split한 것 중 브랜드와 일치하면 추가 부스팅
    for (String word : queryWords) {
      if (!word.trim().isEmpty()) {
        queries.add(Query.of(q -> q.term(t -> t.field("brand").value(word.trim()).boost(3.0f))));
      }
    }

    // 4. 검색어를 공백으로 split한 것 중 카테고리와 일치하면 추가 부스팅
    for (String word : queryWords) {
      if (!word.trim().isEmpty()) {
        queries.add(Query.of(q -> q.term(t -> t.field("category").value(word.trim()).boost(3.0f))));
      }
    }

    return queries;
  }

  /** 필터 조건들 구성 */
  private List<Query> buildFilterQueries(SearchExecuteRequest request) {
    List<Query> filterQueries = new ArrayList<>();

    if (request.getFilters() != null) {
      ProductFiltersDto filters = request.getFilters();
      addTermsFilter(filterQueries, "brand", filters.getBrand());
      addTermsFilter(filterQueries, "category", filters.getCategory());
      addPriceRangeFilter(filterQueries, filters.getPriceRange());
    }

    return filterQueries;
  }

  private void addTermsFilter(List<Query> filterQueries, String field, List<String> values) {
    if (!CollectionUtils.isEmpty(values)) {
      filterQueries.add(
          Query.of(
              q ->
                  q.terms(
                      t ->
                          t.field(field)
                              .terms(
                                  terms ->
                                      terms.value(values.stream().map(FieldValue::of).toList())))));
    }
  }

  private void addPriceRangeFilter(List<Query> filterQueries, PriceRangeDto priceRange) {
    if (priceRange != null && (priceRange.getFrom() != null || priceRange.getTo() != null)) {
      filterQueries.add(
          Query.of(
              q ->
                  q.range(
                      r ->
                          r.number(
                              n ->
                                  n.field("price")
                                      .gte(priceRange.getFrom().doubleValue())
                                      .lte(priceRange.getTo().doubleValue())))));
    }
  }

  private Map<String, Aggregation> buildAggregations(SearchExecuteRequest request) {
    Map<String, Aggregation> aggregations = new HashMap<>();
    aggregations.put("brand", Aggregation.of(a -> a.terms(t -> t.field("brand").size(50))));
    aggregations.put("category", Aggregation.of(a -> a.terms(t -> t.field("category").size(50))));
    return aggregations;
  }

  private SearchRequest buildSearchRequest(
      SearchExecuteRequest request, BoolQuery boolQuery, Map<String, Aggregation> aggregations) {
    ProductSortType sortType =
        request.getSort() != null ? request.getSort().getSortType() : ProductSortType.SCORE;
    SortOrder sortOrder =
        request.getSort() != null
            ? request.getSort().getSortOrder().getSortOrder()
            : SortOrder.Desc;
    int from = (request.getPage() - 1) * request.getSize();

    SearchRequest.Builder searchBuilder =
        new SearchRequest.Builder()
            .index("products")
            .query(Query.of(q -> q.bool(boolQuery)))
            .aggregations(aggregations)
            .from(from)
            .size(request.getSize());

    sortType.applySorting(searchBuilder, sortOrder);

    return searchBuilder.build();
  }

  private List<ProductDto> extractProducts(SearchResponse<JsonNode> response) {
    return response.hits().hits().stream()
        .map(Hit::source)
        .filter(source -> source != null)
        .map(this::convertToProductDocument)
        .filter(document -> document != null)
        .map(this::convertToProductDto)
        .toList();
  }

  private ProductDocument convertToProductDocument(JsonNode source) {
    try {
      return objectMapper.treeToValue(source, ProductDocument.class);
    } catch (Exception e) {
      log.warn("ProductDocument 변환 실패: {}", e.getMessage());
      return null;
    }
  }

  private ProductDto convertToProductDto(ProductDocument document) {
    return ProductDto.builder()
        .id(document.getId())
        .name(document.getName())
        .nameRaw(document.getNameRaw())
        .model(document.getModel())
        .brand(document.getBrand())
        .category(document.getCategory())
        .price(document.getPrice())
        .registeredMonth(document.getRegisteredMonth())
        .reviewCount(
            document.getReviewCount() != null ? Long.valueOf(document.getReviewCount()) : null)
        .thumbnailUrl(document.getThumbnailUrl())
        .description(document.getDescription())
        .descriptionRaw(document.getDescriptionRaw())
        .build();
  }

  private Map<String, List<AggregationBucketDto>> extractAggregations(
      SearchResponse<JsonNode> response) {
    Map<String, List<AggregationBucketDto>> aggregationResults = new HashMap<>();
    if (response.aggregations() != null) {
      response.aggregations().entrySet().stream()
          .filter(entry -> entry.getValue().isSterms())
          .forEach(
              entry -> {
                List<AggregationBucketDto> buckets =
                    entry.getValue().sterms().buckets().array().stream()
                        .map(
                            bucket ->
                                AggregationBucketDto.builder()
                                    .key(bucket.key().stringValue())
                                    .docCount(bucket.docCount())
                                    .build())
                        .toList();
                aggregationResults.put(entry.getKey(), buckets);
              });
    }
    return aggregationResults;
  }

  private SearchExecuteResponse buildSearchResponse(
      SearchExecuteRequest request,
      SearchResponse<JsonNode> response,
      List<ProductDto> products,
      Map<String, List<AggregationBucketDto>> aggregationResults,
      long took) {
    long totalHits = response.hits().total().value();
    int totalPages = (int) Math.ceil((double) totalHits / request.getSize());

    SearchHitsDto hits = SearchHitsDto.builder().total(totalHits).data(products).build();
    SearchMetaDto meta =
        SearchMetaDto.builder()
            .page(request.getPage())
            .size(request.getSize())
            .totalPages(totalPages)
            .processingTime(took)
            .build();

    log.info("상품 검색 완료 - 검색어: {}, 소요시간: {}ms, 결과수: {}", request.getQuery(), took, products.size());

    return SearchExecuteResponse.builder()
        .hits(hits)
        .aggregations(aggregationResults)
        .meta(meta)
        .build();
  }
}
