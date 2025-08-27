package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yjlee.search.search.dto.ProductDto;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchHitsDto;
import com.yjlee.search.search.dto.SearchMetaDto;
import com.yjlee.search.search.service.builder.QueryBuilder;
import com.yjlee.search.search.service.builder.QueryResponseBuilder;
import com.yjlee.search.search.service.builder.SearchRequestBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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

  /** 하이브리드 검색 실행 (BM25 + Vector with RRF) */
  public SearchExecuteResponse hybridSearch(
      String indexName, SearchExecuteRequest request, boolean withExplain) {

    log.info(
        "Starting hybrid search - query: {}, mode: {}, rrfK: {}, topK: {}",
        request.getQuery(),
        request.getSearchMode(),
        request.getRrfK(),
        request.getHybridTopK());

    long startTime = System.currentTimeMillis();

    // 쿼리가 없는 경우 키워드 검색으로 폴백
    if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
      log.debug("Empty query, falling back to keyword-only search");
      return executeKeywordSearch(indexName, request, withExplain);
    }

    try {
      // 1. BM25와 Vector 검색을 동시에 실행
      CompletableFuture<List<Hit<JsonNode>>> bm25Future =
          CompletableFuture.supplyAsync(
              () -> executeBM25Search(indexName, request, request.getHybridTopK()));

      CompletableFuture<List<Hit<JsonNode>>> vectorFuture =
          CompletableFuture.supplyAsync(
              () ->
                  vectorSearchService.searchByVector(
                      indexName,
                      request.getQuery(),
                      request.getHybridTopK(),
                      request.getHybridTopK() * 3) // numCandidates는 k의 3배
              );

      // 두 검색 완료 대기
      List<Hit<JsonNode>> bm25Results = bm25Future.get();
      List<Hit<JsonNode>> vectorResults = vectorFuture.get();

      log.debug(
          "Search results - BM25: {} hits, Vector: {} hits",
          bm25Results.size(),
          vectorResults.size());

      // 2. RRF 병합
      List<RRFScorer.RRFResult> mergedResults =
          rrfScorer.mergeWithRRF(bm25Results, vectorResults, request.getRrfK(), request.getSize());

      // 3. 응답 생성
      long took = System.currentTimeMillis() - startTime;
      SearchExecuteResponse response =
          buildHybridResponse(
              request, mergedResults, bm25Results.size(), vectorResults.size(), took, withExplain);

      log.info("Hybrid search completed in {}ms - final results: {}", took, mergedResults.size());

      return response;

    } catch (InterruptedException | ExecutionException e) {
      log.error("Hybrid search failed", e);
      throw new RuntimeException("Hybrid search failed", e);
    }
  }

  /** BM25 키워드 검색 실행 */
  private List<Hit<JsonNode>> executeBM25Search(
      String indexName, SearchExecuteRequest request, int topK) {

    try {
      BoolQuery boolQuery = queryBuilder.buildBoolQuery(request);

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .query(q -> q.bool(boolQuery))
                      .size(topK)
                      .source(src -> src.filter(f -> f.excludes("name_specs_vector"))));

      SearchResponse<JsonNode> response = elasticsearchClient.search(searchRequest, JsonNode.class);
      return response.hits().hits();

    } catch (IOException e) {
      log.error("BM25 search failed", e);
      return new ArrayList<>();
    }
  }

  /** 키워드 검색만 실행 (폴백용) */
  private SearchExecuteResponse executeKeywordSearch(
      String indexName, SearchExecuteRequest request, boolean withExplain) {

    BoolQuery boolQuery = queryBuilder.buildBoolQuery(request);
    Map<String, Aggregation> aggregations = searchRequestBuilder.buildAggregations();
    SearchRequest searchRequest =
        searchRequestBuilder.buildProductSearchRequest(
            indexName, request, boolQuery, aggregations, withExplain);

    SearchResponse<JsonNode> response = queryExecutor.execute(searchRequest);
    long took = System.currentTimeMillis();

    return responseBuilder.buildSearchResponse(request, response, took, withExplain, searchRequest);
  }

  /** 하이브리드 검색 응답 생성 */
  private SearchExecuteResponse buildHybridResponse(
      SearchExecuteRequest request,
      List<RRFScorer.RRFResult> mergedResults,
      int bm25Count,
      int vectorCount,
      long took,
      boolean withExplain) {

    // ProductDto 리스트 생성
    List<ProductDto> products = new ArrayList<>();
    for (int i = 0; i < mergedResults.size(); i++) {
      RRFScorer.RRFResult result = mergedResults.get(i);
      JsonNode source = result.getDocument().source();

      // JsonNode를 ProductDto로 변환
      ProductDto.ProductDtoBuilder productBuilder =
          ProductDto.builder().id(result.getDocument().id()).score(result.getTotalRrfScore());

      if (source.has("name")) {
        productBuilder.name(source.get("name").asText());
        productBuilder.nameRaw(
            source.has("name_raw") ? source.get("name_raw").asText() : source.get("name").asText());
      }
      if (source.has("model")) {
        productBuilder.model(source.get("model").asText());
      }
      if (source.has("brand_name")) {
        productBuilder.brandName(source.get("brand_name").asText());
      }
      if (source.has("category_name")) {
        productBuilder.categoryName(source.get("category_name").asText());
      }
      if (source.has("price")) {
        productBuilder.price(source.get("price").asInt());
      }
      if (source.has("registered_month")) {
        productBuilder.registeredMonth(source.get("registered_month").asText());
      }
      if (source.has("rating")) {
        productBuilder.rating(new java.math.BigDecimal(source.get("rating").asText()));
      }
      if (source.has("review_count")) {
        productBuilder.reviewCount(source.get("review_count").asInt());
      }
      if (source.has("thumbnail_url")) {
        productBuilder.thumbnailUrl(source.get("thumbnail_url").asText());
      }
      if (source.has("specs")) {
        productBuilder.specs(source.get("specs").asText());
        productBuilder.specsRaw(
            source.has("specs_raw")
                ? source.get("specs_raw").asText()
                : source.get("specs").asText());
      }

      // explain 모드일 때 RRF 점수 설명 추가
      if (withExplain) {
        ObjectNode explainNode = objectMapper.createObjectNode();
        explainNode.putPOJO("hybrid_score", result.getScoreExplanation());
        explainNode.put("hybrid_rank", i + 1);
        productBuilder.explain(explainNode.toString());
      }

      products.add(productBuilder.build());
    }

    // SearchHitsDto 생성
    SearchHitsDto hits =
        SearchHitsDto.builder().total((long) mergedResults.size()).data(products).build();

    // SearchMetaDto 생성
    int totalPages = (int) Math.ceil((double) mergedResults.size() / request.getSize());
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
      debugInfo.put("mergedResults", mergedResults.size());
      queryDsl = debugInfo.toString();
    }

    return SearchExecuteResponse.builder()
        .hits(hits)
        .aggregations(null) // 하이브리드 검색에서는 aggregation 지원 안함
        .meta(meta)
        .queryDsl(queryDsl)
        .build();
  }
}
