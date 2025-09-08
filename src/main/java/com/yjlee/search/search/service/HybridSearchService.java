package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.MsearchResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.msearch.MultiSearchResponseItem;
import co.elastic.clients.elasticsearch.core.msearch.RequestItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.EnvironmentTypeConverter;
import com.yjlee.search.search.constants.SearchBoostConstants;
import com.yjlee.search.search.constants.VectorSearchConstants;
import com.yjlee.search.search.converter.ProductDtoConverter;
import com.yjlee.search.search.dto.ProductDto;
import com.yjlee.search.search.dto.ProductSortDto;
import com.yjlee.search.search.dto.ProductSortOrder;
import com.yjlee.search.search.dto.ProductSortType;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchHitsDto;
import com.yjlee.search.search.dto.SearchMetaDto;
import com.yjlee.search.search.dto.SearchSimulationRequest;
import com.yjlee.search.search.service.builder.QueryBuilder;
import com.yjlee.search.search.service.builder.QueryResponseBuilder;
import com.yjlee.search.search.service.builder.SearchRequestBuilder;
import com.yjlee.search.search.utils.AggregationUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

  private final ElasticsearchClient elasticsearchClient;
  private final QueryBuilder queryBuilder;
  private final SearchRequestBuilder searchRequestBuilder;
  private final QueryResponseBuilder responseBuilder;
  private final SearchQueryExecutor queryExecutor;
  private final VectorSearchService vectorSearchService;
  private final RRFScorer rrfScorer;
  private final ObjectMapper objectMapper;
  private final ProductDtoConverter productDtoConverter;
  private final com.yjlee.search.search.service.builder.query.FilterQueryBuilder filterQueryBuilder;

  /** 하이브리드 검색 실행 (BM25 + Vector with RRF) */
  public SearchExecuteResponse hybridSearch(
      String indexName, SearchExecuteRequest request, boolean withExplain) {

    log.info(
        "Starting hybrid search - query: {}, mode: {}, rrfK: {}, topK: {}, vectorMinScore: {}",
        request.getQuery(),
        request.getSearchMode(),
        request.getRrfK(),
        request.getHybridTopK(),
        request.getVectorMinScore());

    // 환경 타입 결정 - 시뮬레이션이면 해당 환경, 아니면 PROD
    DictionaryEnvironmentType environment = DictionaryEnvironmentType.PROD;
    if (request instanceof SearchSimulationRequest simulationRequest) {
      environment =
          EnvironmentTypeConverter.toDictionaryEnvironmentType(
              simulationRequest.getEnvironmentType());
      log.debug("Hybrid simulation search - using environment: {}", environment);
    }

    long startTime = System.currentTimeMillis();

    try {
      // 1. Multi Search API로 BM25와 Vector 검색을 한번에 실행
      MsearchResponse<JsonNode> msearchResponse =
          executeMultiSearch(
              indexName,
              request,
              environment,
              request.getHybridTopK(),
              request.getVectorMinScore());

      // 검색 결과 추출
      List<Hit<JsonNode>> bm25Results = new ArrayList<>();
      List<Hit<JsonNode>> vectorResults = new ArrayList<>();

      List<MultiSearchResponseItem<JsonNode>> responses = msearchResponse.responses();
      if (responses.size() >= 2) {
        // 첫번째 응답: BM25 검색 결과
        if (responses.get(0).isResult()) {
          bm25Results = responses.get(0).result().hits().hits();
        }
        // 두번째 응답: Vector 검색 결과
        if (responses.get(1).isResult()) {
          vectorResults = responses.get(1).result().hits().hits();
        }
      }

      log.debug(
          "Search results - BM25: {} hits, Vector: {} hits",
          bm25Results.size(),
          vectorResults.size());

      // 2. RRF 병합 - 전체 TopK 결과를 병합
      List<RRFScorer.RRFResult> allMergedResults =
          rrfScorer.mergeWithRRF(
              bm25Results,
              vectorResults,
              request.getRrfK(),
              request.getHybridTopK(),
              request.getBm25Weight());

      // 3. 응답 생성
      long took = System.currentTimeMillis() - startTime;
      SearchExecuteResponse response =
          buildHybridResponse(
              request,
              allMergedResults,
              bm25Results.size(),
              vectorResults.size(),
              took,
              withExplain);

      log.info(
          "Hybrid search completed in {}ms - final results: {}", took, allMergedResults.size());

      return response;

    } catch (IOException e) {
      log.error("Hybrid search failed", e);
      throw new RuntimeException("Hybrid search failed", e);
    }
  }

  /** Multi Search API로 BM25와 Vector 검색 동시 실행 */
  private MsearchResponse<JsonNode> executeMultiSearch(
      String indexName,
      SearchExecuteRequest request,
      DictionaryEnvironmentType environment,
      int topK,
      Double vectorMinScore)
      throws IOException {

    // BM25 쿼리 생성
    BoolQuery boolQuery = queryBuilder.buildBoolQuery(request, environment);

    // 필터 쿼리 생성 (벡터 검색용)
    java.util.List<co.elastic.clients.elasticsearch._types.query_dsl.Query> filterQueries =
        filterQueryBuilder.buildFilterQueries(request.getFilters());

    // 벡터 임베딩 생성
    float[] queryVector = vectorSearchService.getQueryEmbedding(request.getQuery());
    List<Float> queryVectorList = new ArrayList<>();
    for (float f : queryVector) {
      queryVectorList.add(f);
    }

    double minScore =
        vectorMinScore != null ? vectorMinScore : vectorSearchService.getDefaultVectorMinScore();

    // Multi Search 요청 생성
    RequestItem bm25Request = buildBM25RequestItem(indexName, boolQuery, topK);
    RequestItem vectorRequest =
        buildVectorRequestItem(
            indexName,
            queryVectorList,
            topK,
            minScore,
            request.getNameVectorBoost(),
            request.getSpecsVectorBoost(),
            filterQueries);

    MsearchRequest msearchRequest =
        MsearchRequest.of(m -> m.index(indexName).searches(List.of(bm25Request, vectorRequest)));

    log.debug("Executing multi search with BM25 and Vector queries for index: {}", indexName);
    return elasticsearchClient.msearch(msearchRequest, JsonNode.class);
  }

  /** 키워드 검색만 실행 (폴백용) */
  private SearchExecuteResponse executeKeywordSearch(
      String indexName,
      SearchExecuteRequest request,
      DictionaryEnvironmentType environment,
      boolean withExplain) {

    BoolQuery boolQuery = queryBuilder.buildBoolQuery(request, environment);
    Map<String, Aggregation> aggregations = searchRequestBuilder.buildAggregations();
    SearchRequest searchRequest =
        searchRequestBuilder.buildProductSearchRequest(
            indexName, request, boolQuery, aggregations, withExplain);

    SearchResponse<JsonNode> response = queryExecutor.execute(searchRequest);
    long took = System.currentTimeMillis();

    return responseBuilder.buildSearchResponse(request, response, took, withExplain, searchRequest);
  }

  /** BM25 검색 RequestItem 생성 */
  private RequestItem buildBM25RequestItem(String indexName, BoolQuery boolQuery, int topK) {
    return RequestItem.of(
        s ->
            s.header(h -> h.index(indexName))
                .body(
                    b ->
                        b.query(q -> q.bool(boolQuery))
                            .size(topK)
                            .source(
                                src ->
                                    src.filter(
                                        f ->
                                            f.excludes(
                                                VectorSearchConstants
                                                    .getVectorFieldsToExclude())))));
  }

  /** Vector 검색 RequestItem 생성 */
  private RequestItem buildVectorRequestItem(
      String indexName,
      List<Float> queryVector,
      int topK,
      double minScore,
      Float nameBoost,
      Float specsBoost,
      List<Query> filterQueries) {

    return RequestItem.of(
        s ->
            s.header(h -> h.index(indexName))
                .body(
                    b -> {
                      b.size(topK)
                          .minScore(minScore)
                          .knn(
                              k ->
                                  k.field(VectorSearchConstants.NAME_VECTOR_FIELD)
                                      .queryVector(queryVector)
                                      .k(topK)
                                      .numCandidates(Math.max(topK * 3, 100))
                                      .boost(
                                          nameBoost != null
                                              ? nameBoost
                                              : SearchBoostConstants.DEFAULT_NAME_VECTOR_BOOST))
                          .knn(
                              k ->
                                  k.field(VectorSearchConstants.SPECS_VECTOR_FIELD)
                                      .queryVector(queryVector)
                                      .k(topK)
                                      .numCandidates(Math.max(topK * 3, 100))
                                      .boost(
                                          specsBoost != null
                                              ? specsBoost
                                              : SearchBoostConstants.DEFAULT_SPECS_VECTOR_BOOST))
                          .source(
                              src ->
                                  src.filter(
                                      f ->
                                          f.excludes(
                                              VectorSearchConstants.getVectorFieldsToExclude())));

                      // 필터 적용
                      if (filterQueries != null && !filterQueries.isEmpty()) {
                        b.query(q -> q.bool(bool -> bool.filter(filterQueries)));
                      }

                      return b;
                    }));
  }

  /** 하이브리드 검색 응답 생성 */
  private SearchExecuteResponse buildHybridResponse(
      SearchExecuteRequest request,
      List<RRFScorer.RRFResult> mergedResults,
      int bm25Count,
      int vectorCount,
      long took,
      boolean withExplain) {

    // 1. 정렬 처리
    ProductSortType sortType =
        Optional.ofNullable(request.getSort())
            .map(ProductSortDto::getSortType)
            .orElse(ProductSortType.SCORE);

    SortOrder sortOrder =
        Optional.ofNullable(request.getSort())
            .map(ProductSortDto::getSortOrder)
            .map(ProductSortOrder::getSortOrder)
            .orElse(SortOrder.Desc);

    List<RRFScorer.RRFResult> sortedResults = applySorting(mergedResults, sortType, sortOrder);

    // 2. 전체 결과에서 Aggregation 계산 (300개 기준)
    Map<String, List<com.yjlee.search.search.dto.AggregationBucketDto>> aggregations =
        AggregationUtils.calculateFromRRFResults(sortedResults);

    // 3. 페이징 처리
    int page = request.getPage();
    int size = request.getSize();
    int from = page * size;
    int to = Math.min(from + size, sortedResults.size());

    List<RRFScorer.RRFResult> pagedResults =
        from < sortedResults.size() ? sortedResults.subList(from, to) : new ArrayList<>();

    // 3. ProductDto 리스트 생성 (페이징된 결과만)
    List<ProductDto> products = new ArrayList<>();
    for (int i = 0; i < pagedResults.size(); i++) {
      RRFScorer.RRFResult result = pagedResults.get(i);

      ProductDto product =
          productDtoConverter.convert(
              result.getDocument().id(), result.getTotalRrfScore(), result.getDocument().source());

      // explain 모드일 때 RRF 점수 설명 추가
      if (withExplain) {
        ObjectNode explainNode = objectMapper.createObjectNode();
        explainNode.putPOJO("hybrid_score", result.getScoreExplanation());
        explainNode.put("hybrid_rank", from + i + 1);
        product = product.toBuilder().explain(explainNode.toString()).build();
      }

      products.add(product);
    }

    // SearchHitsDto 생성 - 전체 병합 결과 개수 표시
    SearchHitsDto hits =
        SearchHitsDto.builder().total((long) sortedResults.size()).data(products).build();

    // SearchMetaDto 생성
    int totalPages = (int) Math.ceil((double) sortedResults.size() / request.getSize());
    SearchMetaDto meta =
        SearchMetaDto.builder()
            .page(request.getPage())
            .size(request.getSize())
            .totalPages(totalPages)
            .processingTime(took)
            .searchSessionId(request.getSearchSessionId())
            .build();

    // queryDsl 생성 (withExplain일 때만)
    String queryDsl = null;
    if (withExplain) {
      ObjectNode debugInfo = objectMapper.createObjectNode();
      debugInfo.put("searchMode", "HYBRID_RRF");
      debugInfo.put("rrfK", request.getRrfK());
      debugInfo.put("hybridTopK", request.getHybridTopK());
      debugInfo.put("bm25Results", bm25Count);
      debugInfo.put("vectorResults", vectorCount);
      debugInfo.put("mergedResults", sortedResults.size());
      queryDsl = debugInfo.toString();
    }

    return SearchExecuteResponse.builder()
        .hits(hits)
        .aggregations(aggregations)
        .meta(meta)
        .queryDsl(queryDsl)
        .build();
  }

  private List<RRFScorer.RRFResult> applySorting(
      List<RRFScorer.RRFResult> results, ProductSortType sortType, SortOrder sortOrder) {

    // Score 정렬은 이미 RRF 점수 순으로 되어 있으므로 그대로 반환
    if (sortType == ProductSortType.SCORE) {
      return sortOrder == SortOrder.Asc
          ? results.stream()
              .sorted(Comparator.comparing(RRFScorer.RRFResult::getTotalRrfScore))
              .collect(Collectors.toList())
          : results;
    }

    // 필드명 결정
    String fieldName = getFieldName(sortType);

    // Comparator 생성
    Comparator<RRFScorer.RRFResult> comparator = createComparator(fieldName, sortOrder);

    // 2차 정렬: RRF score 내림차순
    comparator =
        comparator.thenComparing(RRFScorer.RRFResult::getTotalRrfScore, Comparator.reverseOrder());

    return results.stream().sorted(comparator).collect(Collectors.toList());
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

  private Comparator<RRFScorer.RRFResult> createComparator(String fieldName, SortOrder sortOrder) {
    Comparator<RRFScorer.RRFResult> comparator =
        Comparator.comparing(
            result -> {
              JsonNode source = result.getDocument().source();
              if (source == null || !source.has(fieldName) || source.get(fieldName).isNull()) {
                return sortOrder == SortOrder.Asc ? Double.MAX_VALUE : Double.MIN_VALUE;
              }
              return source.get(fieldName).asDouble();
            });

    return sortOrder == SortOrder.Desc ? comparator.reversed() : comparator;
  }
}
