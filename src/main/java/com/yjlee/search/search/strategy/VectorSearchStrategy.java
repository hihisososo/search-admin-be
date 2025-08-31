package com.yjlee.search.search.strategy;

import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.search.constants.SearchConstants;
import com.yjlee.search.search.converter.ProductDtoConverter;
import com.yjlee.search.search.dto.*;
import com.yjlee.search.search.service.VectorSearchService;
import com.yjlee.search.search.utils.AggregationUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private final com.yjlee.search.search.service.builder.query.FilterQueryBuilder filterQueryBuilder;

  @Override
  public SearchExecuteResponse search(
      String indexName, SearchExecuteRequest request, boolean withExplain) {
    log.info("Executing multi-field vector search for query: {}", request.getQuery());

    long startTime = System.currentTimeMillis();

    // 필터 쿼리 생성
    java.util.List<co.elastic.clients.elasticsearch._types.query_dsl.Query> filterQueries =
        filterQueryBuilder.buildFilterQueries(request.getFilters());

    // 벡터 검색 설정
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
            .filterQueries(filterQueries)
            .build();

    SearchResponse<JsonNode> response =
        vectorSearchService.multiFieldVectorSearch(indexName, request.getQuery(), config);

    List<Hit<JsonNode>> allHits = response.hits().hits();

    // 페이징 처리
    int page = request.getPage();
    int size = request.getSize();
    int from = page * size;
    int to = Math.min(from + size, allHits.size());

    List<Hit<JsonNode>> pagedHits =
        from < allHits.size() ? allHits.subList(from, to) : new ArrayList<>();

    // ProductDto 변환
    List<ProductDto> products =
        pagedHits.stream().map(productDtoConverter::convert).collect(Collectors.toList());

    // Response 생성
    SearchHitsDto hits =
        SearchHitsDto.builder().total((long) allHits.size()).data(products).build();

    int totalPages = (int) Math.ceil((double) allHits.size() / request.getSize());
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
        AggregationUtils.calculateFromHits(allHits);

    return SearchExecuteResponse.builder()
        .hits(hits)
        .aggregations(aggregations)
        .meta(meta)
        .queryDsl(withExplain ? "Vector search executed" : null)
        .build();
  }

  @Override
  public boolean supports(SearchExecuteRequest request) {
    return request.getSearchMode() == SearchMode.VECTOR_MULTI_FIELD;
  }
}
