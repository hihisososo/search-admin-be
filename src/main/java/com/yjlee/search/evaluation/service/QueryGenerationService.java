package com.yjlee.search.evaluation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.service.LLMQueueManager;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.deployment.model.IndexEnvironment.EnvironmentType;
import com.yjlee.search.evaluation.dto.ProductInfoDto;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.service.IndexResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryGenerationService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexResolver indexResolver;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final LLMQueueManager llmQueueManager;
  private final PromptTemplateLoader promptTemplateLoader;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${generation.query.batch-size:20}")
  private int generationBatchSize;

  private static final int MAX_CONCURRENT_BATCHES = 5;

  public List<String> generateRandomQueries(int count) {
    try {
      log.info("랜덤 쿼리 생성 시작: {}개", count);

      Set<String> existingQueries = new HashSet<>(evaluationQueryRepository.findAllQueryStrings());
      Set<String> generatedQueries = new HashSet<>();

      // 병렬 처리를 위한 배치 수 계산
      int totalBatches = (int) Math.ceil((double) count / generationBatchSize);
      totalBatches = Math.min(totalBatches * 2, count); // 여유있게 더 많이 준비

      List<CompletableFuture<List<String>>> futures = new ArrayList<>();

      // 병렬로 여러 배치 처리
      for (int i = 0; i < totalBatches; i += MAX_CONCURRENT_BATCHES) {
        int currentBatchCount = Math.min(MAX_CONCURRENT_BATCHES, totalBatches - i);

        List<CompletableFuture<List<String>>> batchFutures = new ArrayList<>();
        for (int j = 0; j < currentBatchCount; j++) {
          int remainingQueries = count - generatedQueries.size();
          if (remainingQueries <= 0) break;

          int batchSize = Math.min(generationBatchSize, remainingQueries);
          List<ProductInfoDto> products = fetchRandomProducts(batchSize);

          if (!products.isEmpty()) {
            String prompt = buildBulkQueryPrompt(products);
            CompletableFuture<List<String>> future =
                llmQueueManager
                    .submitSimpleTask(prompt, String.format("쿼리 생성 배치 %d", i + j + 1))
                    .thenApply(this::extractQueriesFromBulkResponse)
                    .exceptionally(
                        ex -> {
                          log.warn("배치 처리 실패", ex);
                          return new ArrayList<>();
                        });
            batchFutures.add(future);
          }
        }

        // 현재 배치 그룹 완료 대기
        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

        // 결과 수집
        for (CompletableFuture<List<String>> future : batchFutures) {
          List<String> batchQueries = future.join();
          log.info("배치 결과: {}개 쿼리", batchQueries.size());

          for (String query : batchQueries) {
            if (generatedQueries.size() >= count) break;

            if (query != null
                && !query.trim().isEmpty()
                && !existingQueries.contains(query)
                && !generatedQueries.contains(query)
                && isValidQuery(query)) {
              generatedQueries.add(query);
              log.debug("생성된 쿼리: {}", query);
            }
          }
        }

        if (generatedQueries.size() >= count) break;
      }

      List<String> result = new ArrayList<>(generatedQueries);
      saveGeneratedQueriesAsList(result);

      log.info("랜덤 쿼리 생성 완료: {}개 생성 (요청: {}개)", result.size(), count);
      return result;

    } catch (Exception e) {
      log.error("⚠️ 랜덤 쿼리 생성 실패", e);
      return new ArrayList<>();
    }
  }

  /** 프리뷰용 쿼리 생성: 저장하지 않고 query-generation.txt 프롬프트로만 생성 결과를 반환 */
  public List<String> generateQueriesPreview(int count) {
    try {
      log.info("[PREVIEW] 쿼리 생성 시작: {}개", count);

      Set<String> generated = new HashSet<>();

      // 병렬 처리를 위한 배치 수 계산
      int totalBatches = (int) Math.ceil((double) count / generationBatchSize);
      totalBatches = Math.min(totalBatches * 2, count);

      // 병렬로 여러 배치 처리
      for (int i = 0; i < totalBatches; i += MAX_CONCURRENT_BATCHES) {
        int currentBatchCount = Math.min(MAX_CONCURRENT_BATCHES, totalBatches - i);

        List<CompletableFuture<List<String>>> batchFutures = new ArrayList<>();
        for (int j = 0; j < currentBatchCount; j++) {
          int remainingQueries = count - generated.size();
          if (remainingQueries <= 0) break;

          int batchSize = Math.min(generationBatchSize, remainingQueries);
          List<ProductInfoDto> products = fetchRandomProducts(batchSize);

          if (!products.isEmpty()) {
            String prompt = buildBulkQueryPrompt(products);
            CompletableFuture<List<String>> future =
                llmQueueManager
                    .submitSimpleTask(prompt, String.format("[PREVIEW] 배치 %d", i + j + 1))
                    .thenApply(this::extractQueriesFromBulkResponse)
                    .exceptionally(
                        ex -> {
                          log.warn("[PREVIEW] 배치 처리 실패", ex);
                          return new ArrayList<>();
                        });
            batchFutures.add(future);
          }
        }

        // 현재 배치 그룹 완료 대기
        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

        // 결과 수집
        for (CompletableFuture<List<String>> future : batchFutures) {
          List<String> batchQueries = future.join();
          log.info("[PREVIEW] 배치 결과: {}개 쿼리", batchQueries.size());

          for (String q : batchQueries) {
            if (generated.size() >= count) break;

            if (q != null && !q.trim().isEmpty() && isValidQuery(q) && !generated.contains(q)) {
              generated.add(q.trim());
              log.debug("[PREVIEW] 쿼리 추가: '{}'", q.trim());
            }
          }
        }

        if (generated.size() >= count) break;
      }

      List<String> result = new ArrayList<>(generated);
      log.info("[PREVIEW] 쿼리 생성 완료: {}개", result.size());
      return result;

    } catch (Exception e) {
      log.error("[PREVIEW] 쿼리 생성 실패", e);
      return new ArrayList<>();
    }
  }

  /** 카테고리 옵션을 적용한 프리뷰 생성(저장 없음) */
  public List<String> generateQueriesPreviewWithCategory(int count, String category) {
    try {
      log.info("[PREVIEW] 쿼리 생성(카테고리) 시작: {}개, category={} ", count, category);

      Set<String> generated = new HashSet<>();

      // 병렬 처리를 위한 배치 수 계산
      int totalBatches = (int) Math.ceil((double) count / generationBatchSize);
      totalBatches = Math.min(totalBatches * 2, count);

      // 병렬로 여러 배치 처리
      for (int i = 0; i < totalBatches; i += MAX_CONCURRENT_BATCHES) {
        int currentBatchCount = Math.min(MAX_CONCURRENT_BATCHES, totalBatches - i);

        List<CompletableFuture<List<String>>> batchFutures = new ArrayList<>();
        for (int j = 0; j < currentBatchCount; j++) {
          int remainingQueries = count - generated.size();
          if (remainingQueries <= 0) break;

          int batchSize = Math.min(generationBatchSize, remainingQueries);
          List<ProductInfoDto> products = fetchRandomProductsByCategory(batchSize, category);

          if (!products.isEmpty()) {
            String prompt = buildBulkQueryPrompt(products);
            CompletableFuture<List<String>> future =
                llmQueueManager
                    .submitSimpleTask(prompt, String.format("[PREVIEW/카테고리] 배치 %d", i + j + 1))
                    .thenApply(this::extractQueriesFromBulkResponse)
                    .exceptionally(
                        ex -> {
                          log.warn("[PREVIEW/카테고리] 배치 처리 실패", ex);
                          return new ArrayList<>();
                        });
            batchFutures.add(future);
          }
        }

        // 현재 배치 그룹 완료 대기
        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

        // 결과 수집
        for (CompletableFuture<List<String>> future : batchFutures) {
          List<String> batchQueries = future.join();
          log.info("[PREVIEW/카테고리] 배치 결과: {}개 쿼리", batchQueries.size());

          for (String q : batchQueries) {
            if (generated.size() >= count) break;

            if (q != null && !q.trim().isEmpty() && isValidQuery(q) && !generated.contains(q)) {
              generated.add(q.trim());
              log.debug("[PREVIEW/카테고리] 쿼리 추가: '{}'", q.trim());
            }
          }
        }

        if (generated.size() >= count) break;
      }

      List<String> result = new ArrayList<>(generated);
      log.info("[PREVIEW] 쿼리 생성(카테고리) 완료: {}개", result.size());
      return result;

    } catch (Exception e) {
      log.error("[PREVIEW] 쿼리 생성(카테고리) 실패", e);
      return new ArrayList<>();
    }
  }

  private List<ProductInfoDto> fetchRandomProductsByCategory(int count, String category) {
    try {
      String indexName = indexResolver.resolveProductIndex(EnvironmentType.DEV);
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(count)
                      .query(
                          q ->
                              q.bool(
                                  b ->
                                      b.must(
                                              m ->
                                                  m.match(
                                                      mm ->
                                                          mm.field(ESFields.CATEGORY)
                                                              .query(category)))
                                          .must(
                                              m ->
                                                  m.functionScore(
                                                      fs ->
                                                          fs.functions(
                                                              f -> f.randomScore(rs -> rs))))))
                      .source(
                          src ->
                              src.filter(
                                  f ->
                                      f.includes(
                                          ESFields.PRODUCT_NAME_RAW, ESFields.PRODUCT_SPECS_RAW))));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);

      return response.hits().hits().stream()
          .map(hit -> hit.source())
          .filter(src -> src != null && isValidProduct(src))
          .map(src -> new ProductInfoDto("", src.getNameRaw().trim(), src.getSpecsRaw()))
          .collect(Collectors.toList());

    } catch (Exception e) {
      log.error("카테고리 랜덤 상품 조회 실패", e);
      throw new RuntimeException("카테고리 랜덤 상품 조회 중 오류가 발생했습니다", e);
    }
  }

  private List<ProductInfoDto> fetchRandomProducts(int count) {
    try {
      String indexName2 = indexResolver.resolveProductIndex(EnvironmentType.DEV);
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName2)
                      .size(count)
                      .query(q -> q.functionScore(fs -> fs.functions(f -> f.randomScore(rs -> rs))))
                      .source(
                          src ->
                              src.filter(
                                  f ->
                                      f.includes(
                                          ESFields.PRODUCT_NAME_RAW, ESFields.PRODUCT_SPECS_RAW))));

      SearchResponse<ProductDocument> response =
          elasticsearchClient.search(request, ProductDocument.class);

      return response.hits().hits().stream()
          .map(hit -> hit.source())
          .filter(src -> src != null && isValidProduct(src))
          .map(src -> new ProductInfoDto("", src.getNameRaw().trim(), src.getSpecsRaw()))
          .collect(Collectors.toList());

    } catch (Exception e) {
      log.error("랜덤 상품 조회 실패", e);
      throw new RuntimeException("랜덤 상품 조회 중 오류가 발생했습니다", e);
    }
  }

  private boolean isValidProduct(ProductDocument product) {
    return product != null
        && product.getNameRaw() != null
        && !product.getNameRaw().trim().isEmpty()
        && product.getSpecsRaw() != null
        && !product.getSpecsRaw().trim().isEmpty();
  }

  private boolean isValidQuery(String query) {
    return query != null && !query.trim().isEmpty() && query.length() <= 100;
  }

  private String buildBulkQueryPrompt(List<ProductInfoDto> products) {
    String template = promptTemplateLoader.loadTemplate("query-generation.txt");

    List<Map<String, String>> productList = new ArrayList<>();
    for (ProductInfoDto product : products) {
      if (isValidProductInfo(product)) {
        Map<String, String> item = new HashMap<>();
        item.put("name", product.getNameRaw());
        item.put("specs", product.getSpecsRaw());
        productList.add(item);
      }
    }

    try {
      Map<String, Object> jsonInput = new HashMap<>();
      jsonInput.put("products", productList);
      String jsonString = objectMapper.writeValueAsString(jsonInput);
      return template.replace("{PRODUCT_LIST}", jsonString);
    } catch (Exception e) {
      log.error("JSON 변환 실패", e);
      // Fallback to old format
      StringBuilder sb = new StringBuilder();
      for (ProductInfoDto product : products) {
        if (isValidProductInfo(product)) {
          sb.append(String.format("%s | %s\n", product.getNameRaw(), product.getSpecsRaw()));
        }
      }
      return template.replace("{PRODUCT_LIST}", sb.toString().trim());
    }
  }

  private boolean isValidProductInfo(ProductInfoDto product) {
    return product != null
        && product.getNameRaw() != null
        && !product.getNameRaw().trim().isEmpty()
        && product.getSpecsRaw() != null
        && !product.getSpecsRaw().trim().isEmpty();
  }

  private List<String> extractQueriesFromBulkResponse(String response) {
    List<String> queries = new ArrayList<>();
    if (response == null || response.trim().isEmpty()) {
      return queries;
    }

    // Try JSON parsing first
    try {
      JsonNode root = objectMapper.readTree(response);
      if (root.has("queries") && root.get("queries").isArray()) {
        JsonNode queriesNode = root.get("queries");
        for (JsonNode queryNode : queriesNode) {
          String query = queryNode.asText().trim();
          if (!query.isEmpty() && isValidQuery(query)) {
            queries.add(query);
          }
        }
        return queries;
      }
    } catch (Exception e) {
      log.debug("JSON 파싱 실패, 텍스트 형식으로 파싱 시도");
    }

    // Fallback to text parsing
    String[] lines = response.split("\n");
    for (String line : lines) {
      String cleaned = line.trim();
      if (!cleaned.isEmpty() && isValidQuery(cleaned)) {
        queries.add(cleaned);
      }
    }

    return queries;
  }

  private void saveGeneratedQueriesAsList(List<String> queries) {
    log.info("생성된 쿼리 저장 시작: {}개", queries.size());

    Set<String> existingQueries = new HashSet<>(evaluationQueryRepository.findAllQueryStrings());

    List<String> newQueries =
        queries.stream()
            .distinct()
            .filter(query -> !existingQueries.contains(query))
            .collect(Collectors.toList());

    int duplicateCount = queries.size() - newQueries.size();
    if (duplicateCount > 0) {
      log.warn("중복 쿼리 {}개 제외됨", duplicateCount);
    }

    if (!newQueries.isEmpty()) {
      List<EvaluationQuery> evaluationQueries =
          newQueries.stream()
              .map(query -> EvaluationQuery.builder().query(query).build())
              .collect(Collectors.toList());

      evaluationQueryRepository.saveAll(evaluationQueries);
      log.info("쿼리 저장 완료: {}개", newQueries.size());
    } else {
      log.info("저장할 새로운 쿼리가 없습니다");
    }
  }
}
