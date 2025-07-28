package com.yjlee.search.evaluation.service;

import static com.yjlee.search.evaluation.constants.EvaluationConstants.EVALUATION_SOURCE_SEARCH;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.model.RelevanceStatus;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.constants.ESFields;
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
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final QueryProductMappingRepository queryProductMappingRepository;
  private final OpenAIEmbeddingService embeddingService;

  @Transactional
  public void generateCandidatesFromSearch() {
    log.info("🔍 전체 모든 쿼리의 정답 후보군 생성 시작 (각 검색방식 100개씩, 최대 300개)");

    // 1. 모든 기존 매핑 삭제
    log.info("기존 매핑 전체 삭제");
    queryProductMappingRepository.deleteAll();

    // 2. 모든 쿼리 조회
    List<EvaluationQuery> queries = evaluationQueryRepository.findAll();
    log.info("총 처리할 쿼리: {}개", queries.size());

    // 3. 벌크 임베딩 생성 (한번에 모든 쿼리)
    List<String> queryTexts =
        queries.stream().map(EvaluationQuery::getQuery).collect(Collectors.toList());

    // 3. 벌크 임베딩 생성 (한번에 모든 쿼리)
    log.info("전체 모든 쿼리 벌크 임베딩 생성 시작: {}개", queryTexts.size());
    List<float[]> allEmbeddings = embeddingService.getBulkEmbeddings(queryTexts);
    log.info("벌크 임베딩 생성 완료");

    // 4. 쿼리별로 후보 생성 (미리 생성한 임베딩 사용)
    List<QueryProductMapping> mappings = new ArrayList<>();

    for (int i = 0; i < queries.size(); i++) {
      EvaluationQuery query = queries.get(i);
      try {
        float[] queryEmbedding = i < allEmbeddings.size() ? allEmbeddings.get(i) : null;
        Set<String> allCandidates =
            collectCandidatesForQueryWithEmbedding(query.getQuery(), queryEmbedding);

        // 각 후보를 개별 매핑으로 저장
        for (String productId : allCandidates) {
          QueryProductMapping mapping =
              QueryProductMapping.builder()
                  .evaluationQuery(query)
                  .productId(productId)
                  .relevanceStatus(RelevanceStatus.UNSPECIFIED) // 미평가로 시작
                  .evaluationSource(EVALUATION_SOURCE_SEARCH)
                  .build();
          mappings.add(mapping);
        }

        log.debug("쿼리 '{}' 처리 완료: {}개 후보 (최대 300개 제한)", query.getQuery(), allCandidates.size());

      } catch (Exception e) {
        log.warn("⚠️ 쿼리 '{}' 처리 실패", query.getQuery(), e);
      }
    }

    queryProductMappingRepository.saveAll(mappings);
    log.info(
        "정답 후보군 생성 완료: {}개 쿼리, {}개 매핑 (각 검색방식 100개씩, 최대 300개)", queries.size(), mappings.size());
  }

  @Transactional
  public void generateCandidatesForSelectedQueries(List<Long> queryIds) {
    log.info("🔍 선택된 쿼리들의 정답 후보군 생성 시작: {}개 (각 검색방식 100개씩, 최대 300개)", queryIds.size());

    List<EvaluationQuery> queries = evaluationQueryRepository.findAllById(queryIds);
    log.info("총 처리할 쿼리: {}개", queries.size());

    // 1. 선택된 쿼리들의 기존 매핑 삭제
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

    // 2. 벌크 임베딩 생성 (선택된 쿼리들만)
    List<String> queryTexts =
        queries.stream().map(EvaluationQuery::getQuery).collect(Collectors.toList());
    log.info("선택된 쿼리의 벌크 임베딩 생성 시작: {}개", queryTexts.size());
    List<float[]> allEmbeddings = embeddingService.getBulkEmbeddings(queryTexts);
    log.info("벌크 임베딩 생성 완료");

    // 3. 쿼리별로 후보 생성 (미리 생성한 임베딩 사용)
    List<QueryProductMapping> mappings = new ArrayList<>();

    for (int i = 0; i < queries.size(); i++) {
      EvaluationQuery query = queries.get(i);
      try {
        float[] queryEmbedding = i < allEmbeddings.size() ? allEmbeddings.get(i) : null;
        Set<String> allCandidates =
            collectCandidatesForQueryWithEmbedding(query.getQuery(), queryEmbedding);

        // 각 후보를 개별 매핑으로 저장
        for (String productId : allCandidates) {
          QueryProductMapping mapping =
              QueryProductMapping.builder()
                  .evaluationQuery(query)
                  .productId(productId)
                  .relevanceStatus(RelevanceStatus.UNSPECIFIED) // 미평가로 시작
                  .evaluationSource(EVALUATION_SOURCE_SEARCH)
                  .build();
          mappings.add(mapping);
        }

        log.debug("쿼리 '{}' 처리 완료: {}개 후보 (최대 300개 제한)", query.getQuery(), allCandidates.size());

      } catch (Exception e) {
        log.warn("⚠️ 쿼리 '{}' 처리 실패", query.getQuery(), e);
      }
    }

    queryProductMappingRepository.saveAll(mappings);
    log.info(
        "선택된 쿼리들의 정답 후보군 생성 완료: {}개 쿼리, {}개 매핑 (각 검색방식 100개씩, 최대 300개)",
        queries.size(),
        mappings.size());
  }

  private Set<String> collectCandidatesForQuery(String query) {
    Set<String> allCandidates = new LinkedHashSet<>();

    // 1. Vector 검색 (결합 컨텐츠: name + specs) - 100개
    allCandidates.addAll(searchByVector(query, "name_specs_vector"));

    // 2. 형태소분석 검색 (name) - 100개
    allCandidates.addAll(searchByAnalyzer(query, "name"));

    // 3. 형태소분석 검색 (specs) - 100개
    allCandidates.addAll(searchByAnalyzer(query, "specs"));

    // 4. Bigram 검색 (name) - 100개
    allCandidates.addAll(searchByBigram(query, "name.bigram"));

    // 5. Bigram 검색 (specs) - 100개
    allCandidates.addAll(searchByBigram(query, "specs.bigram"));

    // 최대 300개로 제한 (중복 제거된 상태에서)
    return allCandidates.stream().limit(300).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<String> collectCandidatesForQueryWithEmbedding(String query, float[] queryEmbedding) {
    Set<String> allCandidates = new LinkedHashSet<>();

    // 1. Vector 검색 (미리 생성한 임베딩 사용) - 100개
    if (queryEmbedding != null) {
      allCandidates.addAll(searchByVectorWithEmbedding(queryEmbedding, "name_specs_vector"));
    }

    // 2. 형태소분석 cross field 검색 (name, specs) - 100개
    allCandidates.addAll(searchByCrossField(query, new String[] {"name", "specs"}));

    // 3. Bigram cross field 검색 (name.bigram, specs.bigram) - 100개
    allCandidates.addAll(searchByCrossField(query, new String[] {"name.bigram", "specs.bigram"}));

    // 최대 300개로 제한 (중복 제거된 상태에서)
    return allCandidates.stream().limit(300).collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private List<String> searchByVector(String query, String vectorField) {
    try {
      float[] embedding = embeddingService.getEmbedding(query);
      return searchByVectorWithEmbedding(embedding, vectorField);
    } catch (Exception e) {
      log.warn("Vector 검색 실패: {}", vectorField, e);
      return new ArrayList<>();
    }
  }

  private List<String> searchByVectorWithEmbedding(float[] embedding, String vectorField) {
    try {
      List<Float> queryVector = new ArrayList<>();
      for (float f : embedding) {
        queryVector.add(f);
      }

      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(ESFields.PRODUCTS_SEARCH_ALIAS)
                      .size(100) // 100개로 수정
                      .minScore(0.85)
                      .query(
                          q ->
                              q.knn(
                                  k ->
                                      k.field(vectorField)
                                          .queryVector(queryVector)
                                          .k(100)
                                          .numCandidates(200))));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);
      return extractProductIds(response);
    } catch (Exception e) {
      log.warn("Vector 검색 실패: {}", vectorField, e);
      return new ArrayList<>();
    }
  }

  private List<String> searchByAnalyzer(String query, String field) {
    try {
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(ESFields.PRODUCTS_SEARCH_ALIAS)
                      .size(100) // 50에서 100으로 수정
                      .query(
                          q ->
                              q.bool(
                                  b -> b.must(m -> m.match(ma -> ma.field(field).query(query))))));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);
      return extractProductIds(response);
    } catch (Exception e) {
      log.warn("형태소분석 검색 실패: {}", field, e);
      return new ArrayList<>();
    }
  }

  private List<String> searchByBigram(String query, String field) {
    try {
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(ESFields.PRODUCTS_SEARCH_ALIAS)
                      .size(100) // 50에서 100으로 수정
                      .query(
                          q ->
                              q.bool(
                                  b -> b.must(m -> m.match(ma -> ma.field(field).query(query))))));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);
      return extractProductIds(response);
    } catch (Exception e) {
      log.warn("Bigram 검색 실패: {}", field, e);
      return new ArrayList<>();
    }
  }

  private List<String> searchByCrossField(String query, String[] fields) {
    try {
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(ESFields.PRODUCTS_SEARCH_ALIAS)
                      .size(100) // 50에서 100으로 수정
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

  private List<String> extractProductIds(SearchResponse<ProductDocument> response) {
    List<String> ids = new ArrayList<>();
    for (Hit<ProductDocument> hit : response.hits().hits()) {
      ids.add(hit.id());
    }
    return ids;
  }
}
