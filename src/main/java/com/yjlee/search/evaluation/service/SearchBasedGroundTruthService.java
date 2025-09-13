package com.yjlee.search.evaluation.service;

import static com.yjlee.search.evaluation.constants.EvaluationConstants.EVALUATION_SOURCE_SEARCH;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.ProductBulkFetchService;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.constants.VectorSearchConstants;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.service.IndexResolver;
import com.yjlee.search.search.service.VectorSearchService;
import com.yjlee.search.search.service.builder.QueryBuilder;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchBasedGroundTruthService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexResolver indexResolver;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final QueryProductMappingRepository queryProductMappingRepository;
  private final VectorSearchService vectorSearchService;
  private final QueryBuilder queryBuilder;
  private final ProductBulkFetchService productBulkFetchService;

  private static final int FIXED_PER_STRATEGY = 301;
  private static final int MAX_PARALLEL_QUERIES = 5;

  // 제한된 스레드 풀로 동시 실행 제어
  private final ExecutorService queryExecutor = Executors.newFixedThreadPool(MAX_PARALLEL_QUERIES);

  public void generateCandidatesFromSearch() {
    generateCandidatesFromSearch(null);
  }

  public void generateCandidatesFromSearch(TaskProgressListener progressListener) {
    List<EvaluationQuery> queries = evaluationQueryRepository.findAll();
    processQueries(queries, progressListener, true);
  }

  public void generateCandidatesForSelectedQueries(List<Long> queryIds) {
    generateCandidatesForSelectedQueries(queryIds, null);
  }

  public void generateCandidatesForSelectedQueries(
      List<Long> queryIds, TaskProgressListener progressListener) {
    List<EvaluationQuery> queries = evaluationQueryRepository.findAllById(queryIds);
    processQueries(queries, progressListener, false);
  }

  private void processQueries(
      List<EvaluationQuery> queries, TaskProgressListener progressListener, boolean isFullProcess) {

    String processType = isFullProcess ? "전체 모든" : "선택된";
    log.info("{} 쿼리의 정답 후보군 생성 시작: {}개", processType, queries.size());

    if (queries.isEmpty()) {
      return;
    }

    // 벌크 임베딩 생성 제거 - VectorSearchService가 캐싱 처리
    // 첫 번째 몇 개 쿼리에 대해 미리 캐시 워밍 (선택적)
    int warmupCount = Math.min(10, queries.size());
    for (int i = 0; i < warmupCount; i++) {
      vectorSearchService.getQueryEmbedding(queries.get(i).getQuery());
    }
    log.info("벡터 검색 캐시 워밍 완료: {}개", warmupCount);

    // 진행률 추적을 위한 AtomicInteger
    AtomicInteger completedCount = new AtomicInteger(0);
    int totalQueries = queries.size();

    // 제한된 스레드 풀로 병렬 처리 - 각 쿼리를 독립적인 트랜잭션에서 처리
    List<CompletableFuture<Void>> futures =
        queries.stream()
            .map(
                query ->
                    CompletableFuture.runAsync(
                        () -> {
                          try {
                            processSingleQuerySimple(query.getId());

                            // 진행률 업데이트
                            int completed = completedCount.incrementAndGet();
                            if (progressListener != null) {
                              try {
                                progressListener.onProgress(completed, totalQueries);
                              } catch (Exception ignored) {
                              }
                            }

                            log.debug("쿼리 '{}' 처리 완료", query.getQuery());

                          } catch (Exception e) {
                            log.warn("쿼리 ID {} 처리 실패", query.getId(), e);
                            // 실패해도 진행률은 업데이트
                            int completed = completedCount.incrementAndGet();
                            if (progressListener != null) {
                              try {
                                progressListener.onProgress(completed, totalQueries);
                              } catch (Exception ignored) {
                              }
                            }
                          }
                        },
                        queryExecutor))
            .collect(Collectors.toList());

    // 모든 작업 완료 대기
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    log.info("{} 쿼리의 정답 후보군 생성 완료", processType);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processSingleQuerySimple(Long queryId) {
    EvaluationQuery query =
        evaluationQueryRepository
            .findById(queryId)
            .orElseThrow(() -> new RuntimeException("쿼리를 찾을 수 없습니다: " + queryId));

    // 기존 매핑 모두 삭제
    List<QueryProductMapping> existingMappings =
        queryProductMappingRepository.findByEvaluationQuery(query);
    queryProductMappingRepository.deleteAll(existingMappings);

    // 단순하게 300개씩 가져와서 합치기
    Map<String, String> allCandidates = new LinkedHashMap<>();

    // BM25 검색 - 300개
    searchByBM25(query.getQuery(), 300).forEach(id -> allCandidates.put(id, "BM25"));

    // 바이그램 검색 - 300개
    String[] bigramFields = {"name.bigram", "specs.bigram", "category.bigram"};
    searchByCrossField(query.getQuery(), bigramFields, 300)
        .forEach(
            id -> {
              if (!allCandidates.containsKey(id)) {
                allCandidates.put(id, "BIGRAM");
              } else {
                allCandidates.put(id, "MULTIPLE");
              }
            });

    // 벡터 검색 - 300개
    searchByVector(query.getQuery(), 300)
        .forEach(
            id -> {
              if (!allCandidates.containsKey(id)) {
                allCandidates.put(id, "VECTOR");
              } else {
                allCandidates.put(id, "MULTIPLE");
              }
            });

    // 상품 정보 일괄 조회
    Map<String, ProductDocument> productMap =
        fetchProductsBulk(new HashSet<>(allCandidates.keySet()));

    // 모든 후보군 저장
    List<QueryProductMapping> mappingsToAdd = new ArrayList<>();
    for (Map.Entry<String, String> entry : allCandidates.entrySet()) {
      String productId = entry.getKey();
      String searchSource = entry.getValue();
      ProductDocument product = productMap.get(productId);

      QueryProductMapping mapping =
          QueryProductMapping.builder()
              .evaluationQuery(query)
              .productId(productId)
              .productName(product != null ? product.getNameRaw() : null)
              .productSpecs(product != null ? product.getSpecsRaw() : null)
              .productCategory(product != null ? product.getCategoryName() : null)
              .searchSource(searchSource)
              .evaluationSource(EVALUATION_SOURCE_SEARCH)
              .build();
      mappingsToAdd.add(mapping);
    }

    if (!mappingsToAdd.isEmpty()) {
      queryProductMappingRepository.saveAll(mappingsToAdd);
      log.info("쿼리 '{}' 수동 생성 완료: {}개 후보군 저장", query.getQuery(), mappingsToAdd.size());
    }
  }

  private List<String> searchByBM25(String query) {
    return searchByBM25(query, FIXED_PER_STRATEGY);
  }

  private List<String> searchByBM25(String query, int size) {
    try {
      String indexName = indexResolver.resolveProductIndex(EnvironmentType.DEV);

      // SearchExecuteRequest 생성 (실제 검색과 동일한 쿼리 생성을 위해)
      SearchExecuteRequest searchRequest = new SearchExecuteRequest();
      searchRequest.setQuery(query);

      // 실제 검색과 동일한 BoolQuery 생성
      co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery boolQuery =
          queryBuilder.buildBoolQuery(searchRequest);

      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(size)
                      .source(
                          src ->
                              src.filter(
                                  f ->
                                      f.excludes(VectorSearchConstants.getVectorFieldsToExclude())))
                      .query(q -> q.bool(boolQuery)));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);
      return extractProductIds(response);
    } catch (Exception e) {
      log.warn("BM25 검색 실패: {}", query, e);
      return new ArrayList<>();
    }
  }

  private List<String> searchByVector(String query) {
    return searchByVector(query, FIXED_PER_STRATEGY);
  }

  private List<String> searchByVector(String query, int size) {
    try {
      String indexName = indexResolver.resolveProductIndex(EnvironmentType.DEV);

      SearchResponse<JsonNode> response =
          vectorSearchService.multiFieldVectorSearch(indexName, query, size);

      // JsonNode 결과에서 상품 ID 추출
      List<String> productIds = new ArrayList<>();
      for (Hit<JsonNode> hit : response.hits().hits()) {
        productIds.add(hit.id());
      }

      return productIds.stream().limit(size).collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Vector 검색 실패: {}", query, e);
      return new ArrayList<>();
    }
  }

  private List<String> searchByCrossField(String query, String[] fields) {
    return searchByCrossField(query, fields, FIXED_PER_STRATEGY);
  }

  private List<String> searchByCrossField(String query, String[] fields, int size) {
    try {
      String indexName = indexResolver.resolveProductIndex(EnvironmentType.DEV);
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(size)
                      .source(
                          src ->
                              src.filter(
                                  f ->
                                      f.excludes(VectorSearchConstants.getVectorFieldsToExclude())))
                      .query(
                          q ->
                              q.multiMatch(
                                  mm ->
                                      mm.query(query) // Bigram 검색을 위해 쿼리 그대로 사용
                                          .fields(List.of(fields))
                                          .operator(Operator.Or)
                                          .minimumShouldMatch("80%")
                                          .type(TextQueryType.CrossFields))));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);
      return extractProductIds(response);
    } catch (Exception e) {
      log.warn("Cross field 검색 실패: {}", String.join(", ", fields), e);
      return new ArrayList<>();
    }
  }

  private List<String> extractProductIds(SearchResponse<ProductDocument> response) {
    List<String> ids = new ArrayList<>();
    for (Hit<ProductDocument> hit : response.hits().hits()) {
      ids.add(hit.id());
    }
    return ids;
  }

  private Map<String, ProductDocument> fetchProductsBulk(Set<String> productIds) {
    if (productIds == null || productIds.isEmpty()) {
      return new HashMap<>();
    }
    return productBulkFetchService.fetchBulk(new ArrayList<>(productIds), EnvironmentType.DEV);
  }

  @PreDestroy
  public void shutdown() {
    log.info("SearchBasedGroundTruthService 스레드 풀 종료 중...");
    queryExecutor.shutdown();
    try {
      if (!queryExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
        queryExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      queryExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
