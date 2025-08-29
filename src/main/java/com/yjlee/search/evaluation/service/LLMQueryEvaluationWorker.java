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
import com.yjlee.search.common.service.LLMQueueManager;
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
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** LLM 평가를 위한 별도 Worker 서비스 Spring AOP 문제 해결을 위해 분리된 서비스 (순환 의존성 방지) */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMQueryEvaluationWorker {

  private final LLMQueueManager llmQueueManager;
  private final ElasticsearchClient elasticsearchClient;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final QueryProductMappingRepository queryProductMappingRepository;
  private final ObjectMapper objectMapper;
  private final PromptTemplateLoader promptTemplateLoader;

  /** ES에서 여러 상품을 한 번에 조회 (벌크 조회) */
  public Map<String, ProductDocument> getProductsBulk(List<String> productIds) {
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

  private List<QueryProductMapping> parseBulkEvaluationResponse(
      String query, List<QueryProductMapping> mappings, String response) {
    List<QueryProductMapping> updatedMappings = new ArrayList<>();

    try {
      String cleanedResponse = cleanJsonResponse(response);
      JsonNode jsonArray = objectMapper.readTree(cleanedResponse);

      if (!jsonArray.isArray()) {
        log.warn("⚠️ LLM 응답이 배열 형식이 아닙니다: {}", response);
        return new ArrayList<>();
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
            // 응답 누락: 미평가로 유지
            continue;
          }

          int score = evaluation.path("score").asInt(0);
          String reason = "자동 평가";
          double confidence = 1.0;

          String evaluationReason =
              String.format("%s (score: %d, confidence: %.2f)", reason, score, confidence);

          QueryProductMapping updatedMapping =
              QueryProductMapping.builder()
                  .id(mapping.getId())
                  .evaluationQuery(mapping.getEvaluationQuery())
                  .productId(mapping.getProductId())
                  .productName(mapping.getProductName())
                  .productSpecs(mapping.getProductSpecs())
                  .relevanceScore(score)
                  .evaluationReason(evaluationReason)
                  .evaluationSource(EVALUATION_SOURCE_LLM)
                  .confidence(confidence)
                  .productCategory(mapping.getProductCategory())
                  .searchSource(mapping.getSearchSource())
                  .build();

          updatedMappings.add(updatedMapping);

        } catch (Exception e) {
          log.warn("⚠️ 상품 {} 평가 결과 파싱 실패 - 미평가로 유지", mapping.getProductId(), e);
          // 파싱 실패: 미평가로 유지
        }
      }

    } catch (Exception e) {
      log.warn("⚠️ 벌크 평가 응답 전체 파싱 실패 - 미평가로 유지: {}", response, e);
      return new ArrayList<>();
    }

    return updatedMappings;
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

  /** 단일 배치 처리 (큐 시스템에서 호출) */
  public void processSingleBatch(
      String query,
      List<ProductDocument> products,
      List<QueryProductMapping> mappings,
      EvaluationQuery evaluationQuery) {
    try {
      log.info("배치 처리 시작: 쿼리='{}', 상품 {}개", query, products.size());

      // 프롬프트 생성
      String prompt =
          buildBulkEvaluationPromptWithMappings(query, products, mappings, evaluationQuery);

      // LLM 호출 - LLMQueueManager 사용
      CompletableFuture<String> future =
          llmQueueManager.submitSimpleTask(
              prompt, String.format("후보군 평가 (쿼리='%s', 상품 %d개)", query, products.size()));
      String response = future.join();

      if (response == null || response.trim().isEmpty()) {
        log.warn("LLM API 응답이 비어있습니다");
        return;
      }

      // 응답 파싱
      List<QueryProductMapping> updatedMappings =
          parseBulkEvaluationResponse(query, mappings, response);

      // DB 저장
      if (!updatedMappings.isEmpty()) {
        queryProductMappingRepository.saveAll(updatedMappings);
        log.info("배치 처리 완료: {}개 매핑 저장", updatedMappings.size());
      }

    } catch (Exception e) {
      // 429 에러는 상위로 전파
      if (e.getMessage() != null && e.getMessage().contains("429")) {
        throw new RuntimeException("Rate limit exceeded", e);
      }
      log.error("배치 처리 실패", e);
    }
  }

  private String buildBulkEvaluationPromptWithMappings(
      String query,
      List<ProductDocument> products,
      List<QueryProductMapping> mappings,
      EvaluationQuery evaluationQuery) {
    // 검색 정보 JSON 생성 (쿼리만 포함)
    StringBuilder searchInfoBuilder = new StringBuilder();
    searchInfoBuilder.append("{\n");
    searchInfoBuilder.append("  \"query\": \"").append(query).append("\"");
    searchInfoBuilder.append("\n}");

    // 상품 리스트 JSON 생성
    StringBuilder productListBuilder = new StringBuilder();
    productListBuilder.append("[\n");
    for (int i = 0; i < products.size(); i++) {
      ProductDocument product = products.get(i);
      QueryProductMapping mapping = i < mappings.size() ? mappings.get(i) : null;

      if (i > 0) productListBuilder.append(",\n");
      productListBuilder.append("  {\n");
      productListBuilder.append("    \"productId\": \"").append(product.getId()).append("\",\n");
      productListBuilder
          .append("    \"name\": \"")
          .append(escapeJson(product.getNameRaw() != null ? product.getNameRaw() : "N/A"))
          .append("\",\n");
      productListBuilder
          .append("    \"category\": \"")
          .append(
              escapeJson(
                  mapping != null && mapping.getProductCategory() != null
                      ? mapping.getProductCategory()
                      : product.getCategoryName() != null ? product.getCategoryName() : "N/A"))
          .append("\",\n");
      productListBuilder
          .append("    \"specs\": \"")
          .append(escapeJson(product.getSpecsRaw() != null ? product.getSpecsRaw() : "N/A"))
          .append("\"\n");
      productListBuilder.append("  }");
    }
    productListBuilder.append("\n]");

    // 템플릿 변수 설정
    Map<String, String> variables = new HashMap<>();
    variables.put("SEARCH_INFO", searchInfoBuilder.toString());
    variables.put("PRODUCT_LIST", productListBuilder.toString());

    return promptTemplateLoader.loadTemplate("bulk-product-relevance-evaluation.txt", variables);
  }

  private String escapeJson(String str) {
    if (str == null) return "";
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
