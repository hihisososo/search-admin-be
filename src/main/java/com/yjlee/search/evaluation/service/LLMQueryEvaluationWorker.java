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
import com.yjlee.search.evaluation.model.RelevanceStatus;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** LLM 평가를 위한 별도 Worker 서비스 Spring AOP 문제 해결을 위해 분리된 서비스 (순환 의존성 방지) */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMQueryEvaluationWorker {

  private final LLMService llmService;
  private final ElasticsearchClient elasticsearchClient;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final QueryProductMappingRepository queryProductMappingRepository;
  private final ObjectMapper objectMapper;
  private final PromptTemplateLoader promptTemplateLoader;

  @Value("${evaluation.llm.batch-size:20}")
  private int defaultBatchSize;

  /** 개별 쿼리를 비동기로 평가 (실제 멀티 쓰레딩) */
  @Async("llmTaskExecutor")
  public CompletableFuture<Void> evaluateQueryAsync(String query) {
    try {
      log.info("🧵 쿼리 '{}' 평가 시작 - 스레드: {}", query, Thread.currentThread().getName());

      // 실제 평가 로직 (동일한 로직이지만 Worker에서 실행)
      evaluateQueryCandidates(query);

      log.info("✅ 쿼리 '{}' 평가 완료 - 스레드: {}", query, Thread.currentThread().getName());
      return CompletableFuture.completedFuture(null);
    } catch (Exception e) {
      log.error("⚠️ 쿼리 '{}'의 후보군 평가 중 오류 발생 - 스레드: {}", query, Thread.currentThread().getName(), e);
      CompletableFuture<Void> failed = new CompletableFuture<>();
      failed.completeExceptionally(e);
      return failed;
    }
  }

  /** 단일 쿼리의 후보군 평가 (실제 LLM 호출) */
  private void evaluateQueryCandidates(String query) {
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

    // 실제 LLM 평가 실행 (LLMCandidateEvaluationService 로직을 직접 구현)
    evaluateWithLLM(query, evaluationQuery, mappings);
  }

  /** LLM을 사용한 실제 평가 로직 */
  private void evaluateWithLLM(
      String query, EvaluationQuery evaluationQuery, List<QueryProductMapping> mappings) {
    // 1. 모든 상품 정보를 ES에서 벌크 조회
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

    // 3. 상품을 설정된 배치 크기로 나누어 처리
    int batchSize = defaultBatchSize;
    List<QueryProductMapping> updatedMappings = new ArrayList<>();

    for (int i = 0; i < products.size(); i += batchSize) {
      int endIndex = Math.min(i + batchSize, products.size());
      List<ProductDocument> batchProducts = products.subList(i, endIndex);
      List<QueryProductMapping> batchMappings = validMappings.subList(i, endIndex);

      try {
        log.info("🔄 배치 처리 시작: {}-{}/{}", i + 1, endIndex, products.size());

        // 배치별 프롬프트 생성
        String batchPrompt = buildBulkEvaluationPrompt(query, batchProducts);

        // 배치별 LLM 호출
        log.info("🤖 LLM API 호출 시작 (배치 크기: {})", batchProducts.size());
        String batchResponse = llmService.callLLMAPI(batchPrompt);

        if (batchResponse == null || batchResponse.trim().isEmpty()) {
          log.warn("⚠️ LLM API 응답이 비어있습니다");
          throw new RuntimeException("LLM API 응답이 비어있습니다");
        }

        log.info("✅ LLM API 응답 수신 (길이: {}자)", batchResponse.length());
        log.debug("LLM 응답 내용: {}", batchResponse);

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

    // 4. 일괄 저장
    if (!updatedMappings.isEmpty()) {
      queryProductMappingRepository.saveAll(updatedMappings);
      log.info("✅ 쿼리 '{}'의 후보군 벌크 평가 완료: {}개 상품", query, updatedMappings.size());
    }
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

      // 응답과 매핑을 순서대로 처리
      for (int i = 0; i < mappings.size(); i++) {
        QueryProductMapping mapping = mappings.get(i);

        try {
          if (i < jsonArray.size()) {
            JsonNode evaluation = jsonArray.get(i);

            boolean isRelevant = evaluation.path("isRelevant").asBoolean(false);
            String reason = evaluation.path("reason").asText("");
            double confidence = evaluation.path("confidence").asDouble(0.0);

            String evaluationReason = String.format("%s (신뢰도: %.2f)", reason, confidence);

            QueryProductMapping updatedMapping =
                QueryProductMapping.builder()
                    .id(mapping.getId())
                    .evaluationQuery(mapping.getEvaluationQuery())
                    .productId(mapping.getProductId())
                    .productName(mapping.getProductName())
                    .productSpecs(mapping.getProductSpecs())
                    .relevanceStatus(RelevanceStatus.fromBoolean(isRelevant))
                    .evaluationReason(evaluationReason)
                    .evaluationSource(EVALUATION_SOURCE_LLM)
                    .build();

            updatedMappings.add(updatedMapping);

          } else {
            // 응답에 해당 상품이 없는 경우
            QueryProductMapping failedMapping = createFailedMapping(mapping, "응답 누락");
            updatedMappings.add(failedMapping);
          }

        } catch (Exception e) {
          log.warn("⚠️ 상품 {} 평가 결과 파싱 실패", mapping.getProductId(), e);
          QueryProductMapping failedMapping =
              createFailedMapping(mapping, "파싱 실패: " + e.getMessage());
          updatedMappings.add(failedMapping);
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
        .relevanceStatus(RelevanceStatus.IRRELEVANT)
        .evaluationReason("평가 실패: " + errorMessage + " (신뢰도: 0.00)")
        .evaluationSource(EVALUATION_SOURCE_LLM)
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
}
