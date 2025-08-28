package com.yjlee.search.evaluation.service;

import static com.yjlee.search.evaluation.constants.EvaluationConstants.EVALUATION_SOURCE_SEARCH;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.service.IndexResolver;
import com.yjlee.search.search.service.VectorSearchService;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

  private static final int FIXED_PER_STRATEGY = 301;
  private static final int FIXED_MAX_TOTAL_PER_QUERY = 300;

  @Transactional
  public void generateCandidatesFromSearch() {
    generateCandidatesFromSearch(null);
  }

  @Transactional
  public void generateCandidatesFromSearch(TaskProgressListener progressListener) {
    queryProductMappingRepository.deleteAll();
    List<EvaluationQuery> queries = evaluationQueryRepository.findAll();
    processQueries(queries, progressListener, true);
  }

  @Transactional
  public void generateCandidatesForSelectedQueries(List<Long> queryIds) {
    generateCandidatesForSelectedQueries(queryIds, null);
  }

  @Transactional
  public void generateCandidatesForSelectedQueries(
      List<Long> queryIds, TaskProgressListener progressListener) {
    List<EvaluationQuery> queries = evaluationQueryRepository.findAllById(queryIds);

    // 선택된 쿼리들의 기존 매핑만 삭제
    queries.forEach(
        query -> {
          List<QueryProductMapping> existingMappings =
              queryProductMappingRepository.findByEvaluationQuery(query);
          if (!existingMappings.isEmpty()) {
            queryProductMappingRepository.deleteAll(existingMappings);
            log.debug("쿼리 '{}'의 기존 매핑 {}개 삭제", query.getQuery(), existingMappings.size());
          }
        });

    processQueries(queries, progressListener, false);
  }

  private void processQueries(
      List<EvaluationQuery> queries, TaskProgressListener progressListener, boolean isFullProcess) {

    String processType = isFullProcess ? "전체 모든" : "선택된";
    log.info("🔍 {} 쿼리의 정답 후보군 생성 시작: {}개", processType, queries.size());

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

    // Thread-safe collections
    List<QueryProductMapping> mappings = new CopyOnWriteArrayList<>();
    List<EvaluationQuery> updatedQueries = new CopyOnWriteArrayList<>();

    // 진행률 추적을 위한 AtomicInteger
    java.util.concurrent.atomic.AtomicInteger completedCount =
        new java.util.concurrent.atomic.AtomicInteger(0);
    int totalQueries = queries.size();

    // 병렬 처리
    queries.parallelStream()
        .forEach(
            query -> {
              try {
                // 후보 수집 - 임베딩은 VectorSearchService에서 내부적으로 처리
                Map<String, String> candidatesWithSource =
                    collectCandidatesWithSourceTracking(query.getQuery(), null);

                // 300개 제한
                Map<String, String> limitedCandidates =
                    candidatesWithSource.entrySet().stream()
                        .limit(FIXED_MAX_TOTAL_PER_QUERY)
                        .collect(
                            LinkedHashMap::new,
                            (m, e) -> m.put(e.getKey(), e.getValue()),
                            LinkedHashMap::putAll);

                // EvaluationQuery 업데이트
                EvaluationQuery updatedQuery =
                    EvaluationQuery.builder()
                        .id(query.getId())
                        .query(query.getQuery())
                        .queryProductMappings(query.getQueryProductMappings())
                        .createdAt(query.getCreatedAt())
                        .updatedAt(query.getUpdatedAt())
                        .build();
                updatedQueries.add(updatedQuery);

                // 상품 정보 일괄 조회 및 매핑 생성
                Map<String, ProductDocument> productMap =
                    fetchProductsBulk(limitedCandidates.keySet());

                limitedCandidates.forEach(
                    (productId, searchSource) -> {
                      ProductDocument product = productMap.get(productId);
                      QueryProductMapping mapping =
                          QueryProductMapping.builder()
                              .evaluationQuery(updatedQuery)
                              .productId(productId)
                              .productName(product != null ? product.getNameRaw() : null)
                              .productSpecs(product != null ? product.getSpecsRaw() : null)
                              .productCategory(product != null ? product.getCategoryName() : null)
                              .searchSource(searchSource)
                              .evaluationSource(EVALUATION_SOURCE_SEARCH)
                              .build();
                      mappings.add(mapping);
                    });

                log.debug(
                    "쿼리 '{}' 처리 완료: 총 {}개 후보 중 {}개 저장",
                    query.getQuery(),
                    candidatesWithSource.size(),
                    limitedCandidates.size());

                // 진행률 업데이트 - AtomicInteger 사용
                int completed = completedCount.incrementAndGet();
                if (progressListener != null) {
                  try {
                    progressListener.onProgress(completed, totalQueries);
                  } catch (Exception ignored) {
                  }
                }

              } catch (Exception e) {
                log.warn("쿼리 '{}' 처리 실패", query.getQuery(), e);
                // 실패해도 진행률은 업데이트
                int completed = completedCount.incrementAndGet();
                if (progressListener != null) {
                  try {
                    progressListener.onProgress(completed, totalQueries);
                  } catch (Exception ignored) {
                  }
                }
              }
            });

    // 일괄 저장
    evaluationQueryRepository.saveAll(updatedQueries);
    queryProductMappingRepository.saveAll(new ArrayList<>(mappings));

    log.info("{} 쿼리 정답 후보군 생성 완료: {}개 쿼리, {}개 매핑", processType, queries.size(), mappings.size());
  }

  public Set<String> getCandidateIdsForQuery(String query) {
    try {
      // 임베딩은 VectorSearchService에서 내부적으로 처리
      return collectCandidatesWithSourceTracking(query, null).keySet();
    } catch (Exception e) {
      log.warn("쿼리 후보 수집 실패: {}", query, e);
      return new LinkedHashSet<>();
    }
  }

  private Map<String, String> collectCandidatesWithSourceTracking(
      String query, float[] queryEmbedding) {
    Map<String, String> productSourceMap = new LinkedHashMap<>();

    // 벡터 검색 - query 문자열 사용 (VectorSearchService가 임베딩 생성 및 캐싱 처리)
    searchByVector(query).forEach(id -> productSourceMap.put(id, "VECTOR"));

    // 형태소 검색
    String[] morphemeFields = {"name_candidate", "specs_candidate", "category_candidate"};
    searchByCrossField(query, morphemeFields)
        .forEach(
            id -> {
              if (!productSourceMap.containsKey(id)) {
                productSourceMap.put(id, "MORPHEME");
              } else if (!"MULTIPLE".equals(productSourceMap.get(id))) {
                productSourceMap.put(id, "MULTIPLE");
              }
            });

    // 바이그램 검색
    String[] bigramFields = {
      "name_candidate.bigram", "specs_candidate.bigram", "category_candidate.bigram"
    };
    searchByCrossField(query, bigramFields)
        .forEach(
            id -> {
              if (!productSourceMap.containsKey(id)) {
                productSourceMap.put(id, "BIGRAM");
              } else if (!"MULTIPLE".equals(productSourceMap.get(id))) {
                productSourceMap.put(id, "MULTIPLE");
              }
            });

    return productSourceMap;
  }

  private List<String> searchByVector(String query) {
    return searchByVector(query, FIXED_PER_STRATEGY);
  }

  private List<String> searchByVector(String query, int size) {
    try {
      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);

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
      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(size)
                      .query(
                          q ->
                              q.multiMatch(
                                  mm ->
                                      mm.query(TextPreprocessor.preprocess(query))
                                          .fields(List.of(fields))
                                          .operator(Operator.And)
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
    Map<String, ProductDocument> productMap = new HashMap<>();

    if (productIds == null || productIds.isEmpty()) {
      return productMap;
    }

    try {
      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);

      var mgetResponse =
          elasticsearchClient.mget(
              m -> m.index(indexName).ids(new ArrayList<>(productIds)), ProductDocument.class);

      for (var doc : mgetResponse.docs()) {
        if (doc.result().found() && doc.result().source() != null) {
          productMap.put(doc.result().id(), doc.result().source());
        }
      }

      log.debug("Bulk fetch 완료: 요청 {}개, 조회 성공 {}개", productIds.size(), productMap.size());

    } catch (Exception e) {
      log.error("Bulk 상품 조회 실패", e);
      // Fallback 제거 - 실패 시 빈 맵 반환
    }

    return productMap;
  }
}
