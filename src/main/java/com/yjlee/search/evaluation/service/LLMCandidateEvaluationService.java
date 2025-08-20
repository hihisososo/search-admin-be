package com.yjlee.search.evaluation.service;

import static com.yjlee.search.evaluation.constants.EvaluationConstants.EVALUATION_SOURCE_LLM;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MgetResponse;
import co.elastic.clients.elasticsearch.core.mget.MultiGetResponseItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMCandidateEvaluationService {

  private final LLMService llmService;
  private final ElasticsearchClient elasticsearchClient;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final QueryProductMappingRepository queryProductMappingRepository;
  private final ObjectMapper objectMapper;
  private final PromptTemplateLoader promptTemplateLoader;

  @Value("${evaluation.llm.batch-size:20}")
  private int defaultBatchSize;

  public void evaluateAllCandidates() {
    log.info("전체 모든 쿼리의 후보군 LLM 평가 시작");

    List<EvaluationQuery> queries = evaluationQueryRepository.findAll();
    if (queries.isEmpty()) {
      log.warn("⚠️ 평가할 쿼리가 없습니다. 먼저 쿼리를 생성해주세요.");
      return;
    }

    log.info("평가 대상 쿼리: {}개", queries.size());
    evaluateCandidatesForQueries(queries.stream().map(EvaluationQuery::getId).toList());

    log.info("전체 모든 쿼리의 후보군 LLM 평가 완료");
  }

  private final LLMQueryEvaluationWorker llmQueryEvaluationWorker;

  public void evaluateCandidatesForQueries(List<Long> queryIds) {
    log.info("선택된 쿼리들의 후보군 LLM 평가 시작 (실제 멀티 쓰레딩): {}개 쿼리", queryIds.size());

    if (queryIds.isEmpty()) {
      log.warn("⚠️ 평가할 쿼리 ID가 없습니다.");
      return;
    }

    List<EvaluationQuery> queries = evaluationQueryRepository.findAllById(queryIds);
    if (queries.isEmpty()) {
      log.warn("⚠️ 유효한 평가 쿼리를 찾을 수 없습니다. 쿼리 ID: {}", queryIds);
      return;
    }

    log.info("유효한 평가 쿼리: {}개 (요청: {}개)", queries.size(), queryIds.size());

    // 각 쿼리별 후보군 개수 확인
    int totalCandidates = 0;
    for (EvaluationQuery query : queries) {
      List<QueryProductMapping> mappings =
          queryProductMappingRepository.findByEvaluationQuery(query);
      int candidateCount = mappings.size();
      totalCandidates += candidateCount;
      log.info("쿼리 '{}': {}개 후보군", query.getQuery(), candidateCount);
    }

    if (totalCandidates == 0) {
      log.warn("⚠️ 평가할 후보군이 없습니다. 먼저 후보군을 생성해주세요.");
      return;
    }

    log.info("총 평가 대상 후보군: {}개", totalCandidates);

    // 진행 카운터
    final int totalQueries = queries.size();
    final java.util.concurrent.atomic.AtomicInteger done =
        new java.util.concurrent.atomic.AtomicInteger(0);

    // 실제 멀티 쓰레딩: Worker 서비스를 통한 비동기 실행 + 진행 로깅
    List<CompletableFuture<Void>> futures =
        queries.stream()
            .map(
                query ->
                    llmQueryEvaluationWorker
                        .evaluateQueryAsync(query.getQuery())
                        .whenComplete(
                            (v, ex) -> {
                              int d = done.incrementAndGet();
                              log.info(
                                  "LLM 평가 진행: {}/{} (쿼리='{}')", d, totalQueries, query.getQuery());
                            }))
            .toList();

    // 모든 작업 완료 대기
    CompletableFuture<Void> allTasks =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

    try {
      allTasks.join();
      log.info("선택된 쿼리들의 후보군 LLM 평가 완료 (실제 멀티 쓰레딩)");
    } catch (Exception e) {
      log.error("병렬 처리 중 오류 발생", e);
      throw new RuntimeException("LLM 평가 병렬 처리 실패", e);
    }
  }

  public void evaluateCandidatesForQueryInternal(String query) {
    log.info("쿼리 '{}'의 후보군 벌크 평가 시작", query);

    Optional<EvaluationQuery> evaluationQueryOpt = evaluationQueryRepository.findByQuery(query);
    if (evaluationQueryOpt.isEmpty()) {
      log.warn("⚠️ 평가 쿼리를 찾을 수 없습니다: {}", query);
      return;
    }

    EvaluationQuery evaluationQuery = evaluationQueryOpt.get();
    List<QueryProductMapping> mappings =
        queryProductMappingRepository.findByEvaluationQuery(evaluationQuery);
    if (mappings.isEmpty()) {
      log.warn("⚠️ 쿼리 '{}'에 대한 후보군이 없습니다. 먼저 후보군을 생성해주세요.", query);
      return;
    }

    log.info("쿼리 '{}': {}개 후보군 평가 시작", query, mappings.size());

    // 1. 모든 상품 정보를 ES에서 벌크 조회 (개선!)
    List<String> productIds = mappings.stream().map(QueryProductMapping::getProductId).toList();

    Map<String, ProductDocument> productMap = getProductsBulk(productIds);
    log.info("🔍 ES 벌크 조회 완료: {}/{}개 상품", productMap.size(), productIds.size());

    // 2. 유효한 매핑만 필터링
    List<ProductDocument> products = new ArrayList<>();
    List<QueryProductMapping> validMappings = new ArrayList<>();

    for (QueryProductMapping mapping : mappings) {
      ProductDocument product = productMap.get(mapping.getProductId());
      if (product != null) {
        products.add(product);
        validMappings.add(mapping);
      } else {
        log.warn("⚠️ 상품을 찾을 수 없습니다: {}", mapping.getProductId());
      }
    }

    if (products.isEmpty()) {
      log.warn("⚠️ 쿼리 '{}'에 대한 유효한 상품이 없습니다", query);
      return;
    }

    // 2. 상품을 설정된 배치 크기로 나누어 처리
    int batchSize = getBatchSize();
    List<QueryProductMapping> updatedMappings = new ArrayList<>();

    for (int i = 0; i < products.size(); i += batchSize) {
      int endIndex = Math.min(i + batchSize, products.size());
      List<ProductDocument> batchProducts = products.subList(i, endIndex);
      List<QueryProductMapping> batchMappings = validMappings.subList(i, endIndex);

      try {
        log.info("🔄 배치 처리 시작: {}-{}/{}", i + 1, endIndex, products.size());

        // 배치별 프롬프트 생성 (20개 상품을 하나의 프롬프트에)
        String batchPrompt = buildBulkEvaluationPrompt(query, batchProducts);

        // 배치별 LLM 호출
        log.info("🤖 LLM API 호출 시작 (배치 크기: {})", batchProducts.size());
        String batchResponse = llmService.callLLMAPI(batchPrompt, null);

        if (batchResponse == null || batchResponse.trim().isEmpty()) {
          log.warn("⚠️ LLM API 응답이 비어있습니다");
          throw new RuntimeException("LLM API 응답이 비어있습니다");
        }

        log.info("✅ LLM API 응답 수신 (길이: {}자)", batchResponse.length());

        // 배치별 응답 파싱
        List<QueryProductMapping> batchResults =
            parseBulkEvaluationResponse(query, batchMappings, batchResponse);

        updatedMappings.addAll(batchResults);

        log.info(
            "✅ 배치 처리 완료: {}-{}/{} (성공: {}개)",
            i + 1,
            endIndex,
            products.size(),
            batchResults.size());

      } catch (Exception e) {
        log.warn("⚠️ 배치 {}-{} 처리 실패", i + 1, endIndex, e);
        // 실패한 배치는 실패 매핑으로 처리
        List<QueryProductMapping> failedBatch =
            createFailedMappings(query, batchMappings, "배치 처리 실패: " + e.getMessage());
        updatedMappings.addAll(failedBatch);
      }
    }

    // 5. 일괄 저장
    if (!updatedMappings.isEmpty()) {
      queryProductMappingRepository.saveAll(updatedMappings);
      log.info("✅ 쿼리 '{}'의 후보군 벌크 평가 완료: {}개 상품", query, updatedMappings.size());
    }
  }

  private ProductDocument getProductFromES(String productId) {
    try {
      GetRequest request =
          GetRequest.of(g -> g.index(ESFields.PRODUCTS_SEARCH_ALIAS).id(productId));

      GetResponse<ProductDocument> response =
          elasticsearchClient.get(request, ProductDocument.class);
      return response.found() ? response.source() : null;
    } catch (Exception e) {
      log.warn("⚠️ ES에서 상품 {} 조회 실패", productId, e);
      return null;
    }
  }

  private String buildBulkEvaluationPrompt(String query, List<ProductDocument> products) {
    // 상품 리스트 문자열 생성
    StringBuilder productListBuilder = new StringBuilder();
    for (int i = 0; i < products.size(); i++) {
      ProductDocument product = products.get(i);
      productListBuilder.append("상품 ").append(i + 1).append(":\n");
      productListBuilder.append("- ID: ").append(product.getId()).append("\n");
      productListBuilder
          .append("- 상품명: ")
          .append(product.getNameRaw() != null ? product.getNameRaw() : "N/A")
          .append("\n");
      productListBuilder
          .append("- 스펙: ")
          .append(product.getSpecsRaw() != null ? product.getSpecsRaw() : "N/A")
          .append("\n\n");
    }

    // 템플릿 변수 설정
    Map<String, String> variables = new HashMap<>();
    variables.put("QUERY", query);
    variables.put("PRODUCT_COUNT", String.valueOf(products.size()));
    variables.put("PRODUCT_LIST", productListBuilder.toString());

    return promptTemplateLoader.loadTemplate("bulk-product-relevance-evaluation.txt", variables);
  }

  private List<QueryProductMapping> parseBulkEvaluationResponse(
      String query, List<QueryProductMapping> mappings, String response) {
    List<QueryProductMapping> updatedMappings = new ArrayList<>();

    try {
      String cleanedResponse = cleanJsonResponse(response);
      JsonNode jsonArray = objectMapper.readTree(cleanedResponse);

      if (!jsonArray.isArray()) {
        log.warn("⚠️ LLM 응답이 배열 형식이 아닙니다: {}", response);
        return createFailedMappings(query, mappings, "응답 형식 오류");
      }

      // productId -> evaluation 매핑 생성 (순서에 의존하지 않도록)
      java.util.Map<String, JsonNode> idToEval = new java.util.HashMap<>();
      for (JsonNode node : jsonArray) {
        String pid = node.path("productId").asText(null);
        if (pid != null && !pid.isBlank()) {
          idToEval.put(pid, node);
        }
      }

      // 매핑 리스트 순서를 기준으로 productId로 매칭
      for (QueryProductMapping mapping : mappings) {
        try {
          JsonNode evaluation = idToEval.get(mapping.getProductId());
          if (evaluation == null) {
            updatedMappings.add(createFailedMapping(mapping, "응답 누락"));
            continue;
          }

          int score = evaluation.path("score").asInt(0); // 0/1/2
          String reason = evaluation.path("reason").asText("");
          double confidence = evaluation.path("confidence").asDouble(0.5); // 기본값 0.5

          // confidence가 0.8 이하면 사람 확인 필요(-1)
          int finalScore = confidence <= 0.8 ? -1 : score;
          String evaluationReason =
              String.format("%s (score: %d, confidence: %.2f)", reason, score, confidence);

          QueryProductMapping updatedMapping =
              QueryProductMapping.builder()
                  .id(mapping.getId())
                  .evaluationQuery(mapping.getEvaluationQuery())
                  .productId(mapping.getProductId())
                  .productName(mapping.getProductName())
                  .productSpecs(mapping.getProductSpecs())
                  .relevanceScore(finalScore)
                  .evaluationReason(evaluationReason)
                  .evaluationSource(EVALUATION_SOURCE_LLM)
                  .confidence(confidence)
                  .build();

          updatedMappings.add(updatedMapping);

        } catch (Exception e) {
          log.warn("⚠️ 상품 {} 평가 결과 파싱 실패", mapping.getProductId(), e);
          updatedMappings.add(createFailedMapping(mapping, "파싱 실패: " + e.getMessage()));
        }
      }

    } catch (Exception e) {
      log.warn("⚠️ 벌크 평가 응답 전체 파싱 실패: {}", response, e);
      return createFailedMappings(query, mappings, "전체 파싱 실패: " + e.getMessage());
    }

    return updatedMappings;
  }

  private List<QueryProductMapping> createFailedMappings(
      String query, List<QueryProductMapping> mappings, String errorMessage) {
    List<QueryProductMapping> failedMappings = new ArrayList<>();

    for (QueryProductMapping mapping : mappings) {
      QueryProductMapping failedMapping = createFailedMapping(mapping, errorMessage);
      failedMappings.add(failedMapping);
    }

    return failedMappings;
  }

  private QueryProductMapping createFailedMapping(
      QueryProductMapping mapping, String errorMessage) {
    return QueryProductMapping.builder()
        .id(mapping.getId())
        .evaluationQuery(mapping.getEvaluationQuery())
        .productId(mapping.getProductId())
        .productName(mapping.getProductName())
        .productSpecs(mapping.getProductSpecs())
        .relevanceScore(-1) // 평가 실패시 사람 확인 필요
        .evaluationReason("평가 실패: " + errorMessage + " (신뢰도: 0.00)")
        .evaluationSource(EVALUATION_SOURCE_LLM)
        .confidence(0.0)
        .build();
  }

  private String cleanJsonResponse(String response) {
    if (response == null || response.trim().isEmpty()) {
      return "{}";
    }

    String cleaned = response.trim();

    // Markdown 코드 블록 제거
    if (cleaned.startsWith("```json")) {
      cleaned = cleaned.substring(7);
    } else if (cleaned.startsWith("```")) {
      cleaned = cleaned.substring(3);
    }

    if (cleaned.endsWith("```")) {
      cleaned = cleaned.substring(0, cleaned.length() - 3);
    }

    return cleaned.trim();
  }

  private int getBatchSize() {
    return defaultBatchSize;
  }

  /** ES에서 여러 상품을 한 번에 조회 (벌크 조회) */
  private Map<String, ProductDocument> getProductsBulk(List<String> productIds) {
    if (productIds.isEmpty()) {
      return new HashMap<>();
    }

    try {
      log.debug("🔍 ES 벌크 조회 시작: {}개 상품", productIds.size());

      // MultiGet 요청 생성
      MgetRequest.Builder requestBuilder =
          new MgetRequest.Builder().index(ESFields.PRODUCTS_SEARCH_ALIAS);

      // 각 상품 ID를 요청에 추가
      for (String productId : productIds) {
        requestBuilder.ids(productId);
      }

      MgetRequest request = requestBuilder.build();
      MgetResponse<ProductDocument> response =
          elasticsearchClient.mget(request, ProductDocument.class);

      // 응답을 Map으로 변환
      Map<String, ProductDocument> productMap = new HashMap<>();
      for (MultiGetResponseItem<ProductDocument> item : response.docs()) {
        if (item.result() != null && item.result().found()) {
          String productId = item.result().id();
          ProductDocument product = item.result().source();
          if (product != null) {
            productMap.put(productId, product);
          }
        }
      }

      log.debug("🔍 ES 벌크 조회 완료: {}/{}개 상품 조회 성공", productMap.size(), productIds.size());
      return productMap;

    } catch (Exception e) {
      log.error("⚠️ ES 벌크 조회 실패", e);
      // 벌크 조회 실패 시 개별 조회로 폴백
      log.warn("벌크 조회 실패, 개별 조회로 폴백 실행");
      return getProductsIndividually(productIds);
    }
  }

  /** 벌크 조회 실패 시 개별 조회로 폴백 */
  private Map<String, ProductDocument> getProductsIndividually(List<String> productIds) {
    Map<String, ProductDocument> productMap = new HashMap<>();

    for (String productId : productIds) {
      ProductDocument product = getProductFromES(productId);
      if (product != null) {
        productMap.put(productId, product);
      }
    }

    return productMap;
  }
}
