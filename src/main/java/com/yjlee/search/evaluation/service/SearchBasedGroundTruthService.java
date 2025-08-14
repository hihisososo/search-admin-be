package com.yjlee.search.evaluation.service;

import static com.yjlee.search.evaluation.constants.EvaluationConstants.EVALUATION_SOURCE_SEARCH;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.yjlee.search.common.util.TextPreprocessor;
import org.springframework.beans.factory.annotation.Value;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.model.RelevanceStatus;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.service.IndexResolver;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
  private final OpenAIEmbeddingService embeddingService;

  @Value("${app.evaluation.candidate.per-strategy-fetch-size:200}")
  private int perStrategyFetchSize;

  @Value("${app.evaluation.candidate.vector-num-candidates:400}")
  private int vectorNumCandidates;

  @Value("${app.evaluation.candidate.min-score:0.85}")
  private double vectorMinScore;

  @Value("${app.evaluation.candidate.max-total-per-query:300}")
  private int maxTotalPerQuery;

  @Transactional
  public void generateCandidatesFromSearch() {
    log.info(
        "🔍 전체 모든 쿼리의 정답 후보군 생성 시작 (각 검색방식 {}개씩, 최대 {}개)",
        perStrategyFetchSize,
        maxTotalPerQuery);

    log.info("기존 매핑 전체 삭제");
    queryProductMappingRepository.deleteAll();

    List<EvaluationQuery> queries = evaluationQueryRepository.findAll();
    log.info("총 처리할 쿼리: {}개", queries.size());

    List<String> queryTexts =
        queries.stream().map(EvaluationQuery::getQuery).collect(Collectors.toList());

    log.info("전체 모든 쿼리 벌크 임베딩 생성 시작: {}개", queryTexts.size());
    List<float[]> allEmbeddings = embeddingService.getBulkEmbeddings(queryTexts);
    log.info("벌크 임베딩 생성 완료");

    List<QueryProductMapping> mappings = new ArrayList<>();

    for (int i = 0; i < queries.size(); i++) {
      EvaluationQuery query = queries.get(i);
      try {
        float[] queryEmbedding = i < allEmbeddings.size() ? allEmbeddings.get(i) : null;
        Set<String> allCandidates =
            collectCandidatesForQueryWithEmbedding(query.getQuery(), queryEmbedding);

        for (String productId : allCandidates) {
          ProductDocument product = fetchProduct(productId);
          QueryProductMapping mapping =
              QueryProductMapping.builder()
                  .evaluationQuery(query)
                  .productId(productId)
                  .productName(product != null ? product.getNameRaw() : null)
                  .productSpecs(product != null ? product.getSpecsRaw() : null)
                  .relevanceStatus(RelevanceStatus.UNSPECIFIED)
                  .evaluationSource(EVALUATION_SOURCE_SEARCH)
                  .build();
          mappings.add(mapping);
        }

        log.debug(
            "쿼리 '{}' 처리 완료: {}개 후보 (최대 {}개 제한)",
            query.getQuery(),
            allCandidates.size(),
            maxTotalPerQuery);

      } catch (Exception e) {
        log.warn("⚠️ 쿼리 '{}' 처리 실패", query.getQuery(), e);
      }
    }

    queryProductMappingRepository.saveAll(mappings);
    log.info(
        "정답 후보군 생성 완료: {}개 쿼리, {}개 매핑 (각 검색방식 {}개씩, 최대 {}개)",
        queries.size(),
        mappings.size(),
        perStrategyFetchSize,
        maxTotalPerQuery);
  }

  @Transactional
  public void generateCandidatesForSelectedQueries(List<Long> queryIds) {
    log.info(
        "🔍 선택된 쿼리들의 정답 후보군 생성 시작: {}개 (각 검색방식 {}개씩, 최대 {}개)",
        queryIds.size(),
        perStrategyFetchSize,
        maxTotalPerQuery);

    List<EvaluationQuery> queries = evaluationQueryRepository.findAllById(queryIds);
    log.info("총 처리할 쿼리: {}개", queries.size());

    if (!queries.isEmpty()) {
      log.info("선택된 쿼리들의 기존 매핑 삭제: {}개", queries.size());
      for (EvaluationQuery query : queries) {
        List<QueryProductMapping> existingMappings =
            queryProductMappingRepository.findByEvaluationQuery(query);
        if (!existingMappings.isEmpty()) {
          queryProductMappingRepository.deleteAll(existingMappings);
          log.debug("쿼리 '{}'의 기존 매핑 {}개 삭제", query.getQuery(), existingMappings.size());
        }
      }
    }

    List<String> queryTexts =
        queries.stream().map(EvaluationQuery::getQuery).collect(Collectors.toList());
    log.info("선택된 쿼리의 벌크 임베딩 생성 시작: {}개", queryTexts.size());
    List<float[]> allEmbeddings = embeddingService.getBulkEmbeddings(queryTexts);
    log.info("벌크 임베딩 생성 완료");

    List<QueryProductMapping> mappings = new ArrayList<>();

    for (int i = 0; i < queries.size(); i++) {
      EvaluationQuery query = queries.get(i);
      try {
        float[] queryEmbedding = i < allEmbeddings.size() ? allEmbeddings.get(i) : null;
        Set<String> allCandidates =
            collectCandidatesForQueryWithEmbedding(query.getQuery(), queryEmbedding);

        for (String productId : allCandidates) {
          ProductDocument product = fetchProduct(productId);
          QueryProductMapping mapping =
              QueryProductMapping.builder()
                  .evaluationQuery(query)
                  .productId(productId)
                  .productName(product != null ? product.getNameRaw() : null)
                  .productSpecs(product != null ? product.getSpecsRaw() : null)
                  .relevanceStatus(RelevanceStatus.UNSPECIFIED)
                  .evaluationSource(EVALUATION_SOURCE_SEARCH)
                  .build();
          mappings.add(mapping);
        }

        log.debug(
            "쿼리 '{}' 처리 완료: {}개 후보 (최대 {}개 제한)",
            query.getQuery(),
            allCandidates.size(),
            maxTotalPerQuery);

      } catch (Exception e) {
        log.warn("⚠️ 쿼리 '{}' 처리 실패", query.getQuery(), e);
      }
    }

    queryProductMappingRepository.saveAll(mappings);
    log.info(
        "선택된 쿼리들의 정답 후보군 생성 완료: {}개 쿼리, {}개 매핑 (각 검색방식 {}개씩, 최대 {}개)",
        queries.size(),
        mappings.size(),
        perStrategyFetchSize,
        maxTotalPerQuery);
  }

  /** 저장 없이 쿼리의 후보 상품 ID 집합을 계산하여 반환 (드라이런) 최대 300개 제한 로직을 그대로 따릅니다. */
  public Set<String> getCandidateIdsForQuery(String query) {
    try {
      float[] embedding = null;
      try {
        embedding = embeddingService.getEmbedding(query);
      } catch (Exception e) {
        log.warn("임베딩 생성 실패, 임베딩 없이 후보 수집 진행: {}", query);
      }

      return collectCandidatesForQueryWithEmbedding(query, embedding);
    } catch (Exception e) {
      log.warn("쿼리 후보 드라이런 실패: {}", query, e);
      return new LinkedHashSet<>();
    }
  }

  /**
   * 동적으로 최대 후보수(targetMaxTotal)를 기준으로 약간 여유 있게 더 많이 가져와서 판단할 때 사용.
   * per-strategy 페치 개수는 (targetMaxTotal * 1.3 / 3)로 계산하고, 벡터 numCandidates는 그 2배로 설정.
   */
  public Set<String> getCandidateIdsForQuery(String query, int targetMaxTotal) {
    try {
      float[] embedding = null;
      try {
        embedding = embeddingService.getEmbedding(query);
      } catch (Exception e) {
        log.warn("임베딩 생성 실패, 임베딩 없이 후보 수집 진행: {}", query);
      }

      // 각 전략이 단독으로도 상한을 초과하는지 검출하기 위해, 전략별 개수를 '상한보다 조금 더' 크게 가져온다
      int dynamicPerStrategy = Math.max(targetMaxTotal + 20, (int) Math.ceil(targetMaxTotal * 1.1));
      int dynamicNumCandidates = Math.max(dynamicPerStrategy * 2, targetMaxTotal + 50);
      double dynamicMinScore = vectorMinScore;
      int dynamicMaxTotal = Math.max(targetMaxTotal + 20, (int) Math.ceil(targetMaxTotal * 1.2));

      return collectCandidatesForQueryWithEmbedding(
          query, embedding, dynamicPerStrategy, dynamicNumCandidates, dynamicMinScore, dynamicMaxTotal);
    } catch (Exception e) {
      log.warn("쿼리 후보 드라이런 실패(동적): {}", query, e);
      return new LinkedHashSet<>();
    }
  }

  private Set<String> collectCandidatesForQueryWithEmbedding(String query, float[] queryEmbedding) {
    Set<String> allCandidates = new LinkedHashSet<>();

    if (queryEmbedding != null) {
      allCandidates.addAll(searchByVectorWithEmbedding(queryEmbedding, "name_specs_vector"));
    }

    allCandidates.addAll(searchByCrossField(query, new String[] {"name", "specs"}));

    allCandidates.addAll(searchByCrossField(query, new String[] {"name.bigram", "specs.bigram"}));

    return allCandidates.stream()
        .limit(maxTotalPerQuery)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  // 동적 파라미터 버전
  private Set<String> collectCandidatesForQueryWithEmbedding(
      String query,
      float[] queryEmbedding,
      int perStrategy,
      int numCandidates,
      double minScore,
      int maxTotal) {
    Set<String> allCandidates = new LinkedHashSet<>();

    if (queryEmbedding != null) {
      allCandidates.addAll(
          searchByVectorWithEmbedding(queryEmbedding, "name_specs_vector", perStrategy, numCandidates, minScore));
    }

    allCandidates.addAll(searchByCrossField(query, new String[] {"name", "specs"}, perStrategy));

    allCandidates.addAll(searchByCrossField(query, new String[] {"name.bigram", "specs.bigram"}, perStrategy));

    return allCandidates.stream()
        .limit(maxTotal)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private List<String> searchByVectorWithEmbedding(float[] embedding, String vectorField) {
    try {
      List<Float> queryVector = new ArrayList<>();
      for (float f : embedding) {
        queryVector.add(f);
      }

      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(perStrategyFetchSize)
                      .minScore(vectorMinScore)
                      .query(
                          q ->
                              q.knn(
                                  k ->
                                      k.field(vectorField)
                                          .queryVector(queryVector)
                                          .k(perStrategyFetchSize)
                                          .numCandidates(vectorNumCandidates))));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);
      return extractProductIds(response);
    } catch (Exception e) {
      log.warn("Vector 검색 실패: {}", vectorField, e);
      return new ArrayList<>();
    }
  }

  // 동적 파라미터 버전
  private List<String> searchByVectorWithEmbedding(
      float[] embedding, String vectorField, int perStrategy, int numCandidates, double minScore) {
    try {
      List<Float> queryVector = new ArrayList<>();
      for (float f : embedding) {
        queryVector.add(f);
      }

      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(perStrategy)
                      .minScore(minScore)
                      .query(
                          q ->
                              q.knn(
                                  k ->
                                      k.field(vectorField)
                                          .queryVector(queryVector)
                                          .k(perStrategy)
                                          .numCandidates(numCandidates))));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);
      return extractProductIds(response);
    } catch (Exception e) {
      log.warn("Vector 검색 실패(동적): {}", vectorField, e);
      return new ArrayList<>();
    }
  }

  private List<String> searchByCrossField(String query, String[] fields) {
    try {
      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(perStrategyFetchSize)
                      .query(
                          q ->
                              q.multiMatch(
                                  mm ->
                                      mm.query(TextPreprocessor.preprocess(query))
                                          .fields(fields[0], fields[1])
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

  // 동적 파라미터 버전
  private List<String> searchByCrossField(String query, String[] fields, int perStrategy) {
    try {
      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(perStrategy)
                      .query(
                          q ->
                              q.multiMatch(
                                  mm ->
                                      mm.query(TextPreprocessor.preprocess(query))
                                          .fields(fields[0], fields[1])
                                          .operator(Operator.And)
                                          .type(TextQueryType.CrossFields))));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);
      return extractProductIds(response);
    } catch (Exception e) {
      log.warn("Cross field 검색 실패(동적): {}", String.join(", ", fields), e);
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

  private ProductDocument fetchProduct(String productId) {
    try {
      String indexName = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);
      var res =
          elasticsearchClient.get(g -> g.index(indexName).id(productId), ProductDocument.class);
      return res.found() ? res.source() : null;
    } catch (Exception e) {
      log.warn("상품 상세 조회 실패: {}", productId);
      return null;
    }
  }
}
