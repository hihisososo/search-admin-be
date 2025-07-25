package com.yjlee.search.evaluation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.evaluation.dto.ProductInfoDto;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import com.yjlee.search.search.constants.ESFields;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroundTruthGenerationService {

  private static final int BATCH_SIZE = 5;
  private static final String GROUND_TRUTH_TEMPLATE_FILE = "ground-truth-mapping.txt";
  private static final String SCROLL_TIMEOUT = "5m";
  private static final String LLM_LOG_DIR = "logs/llm/ground-truth";
  private static final Object LOG_FILE_LOCK = new Object();

  private final ElasticsearchClient elasticsearchClient;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final QueryProductMappingRepository queryProductMappingRepository;
  private final LLMService llmService;
  private final PromptTemplateLoader promptTemplateLoader;

  public void generateGroundTruth() {
    try {
      log.info("🚀 정답셋 생성 시작");
      ensureLLMLogDirectory();
      initializeLogFile();

      List<EvaluationQuery> allQueries =
          evaluationQueryRepository.findAll().stream().limit(100).collect(Collectors.toList());
      log.info("📊 쿼리 조회 완료: {}개", allQueries.size());

      List<ProductInfoDto> allProducts = fetchAllProducts();
      log.info("📊 상품 조회 완료: {}개", allProducts.size());

      Map<String, List<String>> queryToProductsMap = new ConcurrentHashMap<>();
      List<List<EvaluationQuery>> queryBatches = createQueryBatches(allQueries);
      List<List<ProductInfoDto>> productBatches = createProductBatches(allProducts);

      int totalBatches = queryBatches.size() * productBatches.size();
      log.info(
          "📊 총 {}개 배치 처리 예정 (쿼리 배치: {}, 상품 배치: {})",
          totalBatches,
          queryBatches.size(),
          productBatches.size());

      var executor = Executors.newFixedThreadPool(10);
      List<Future<Void>> futures = new ArrayList<>();
      java.util.concurrent.atomic.AtomicInteger batchCounter =
          new java.util.concurrent.atomic.AtomicInteger(0);

      for (int qi = 0; qi < queryBatches.size(); qi++) {
        for (int pi = 0; pi < productBatches.size(); pi++) {
          final int queryBatchIndex = qi;
          final int productBatchIndex = pi;
          final List<EvaluationQuery> queryBatch = queryBatches.get(qi);
          final List<ProductInfoDto> productBatch = productBatches.get(pi);

          futures.add(
              executor.submit(
                  () -> {
                    int currentBatch = batchCounter.incrementAndGet();
                    log.info(
                        "🔄 배치 처리 중: {}/{} (쿼리배치: {}, 상품배치: {}, 쿼리: {}개, 상품: {}개)",
                        currentBatch,
                        totalBatches,
                        queryBatchIndex + 1,
                        productBatchIndex + 1,
                        queryBatch.size(),
                        productBatch.size());

                    List<QueryProductMapping> batchMappings =
                        processQueryProductBatch(queryBatch, productBatch);
                    for (QueryProductMapping mapping : batchMappings) {
                      queryToProductsMap.merge(
                          mapping.getQuery(),
                          Arrays.asList(mapping.getRelevantProductIds().split(",")),
                          (existing, newList) -> {
                            List<String> combined = new ArrayList<>(existing);
                            combined.addAll(newList);
                            return combined.stream().distinct().collect(Collectors.toList());
                          });
                    }

                    log.debug(
                        "✅ 배치 완료: {}/{} (생성된 매핑: {}개)",
                        currentBatch,
                        totalBatches,
                        batchMappings.size());
                    return null;
                  }));
        }
      }

      int completedBatches = 0;
      for (var future : futures) {
        try {
          future.get();
          completedBatches++;
          if (completedBatches % 10 == 0 || completedBatches == totalBatches) {
            double progress = (double) completedBatches / totalBatches * 100;
            log.info("📈 진행률: {}/{} ({:.1f}%)", completedBatches, totalBatches, progress);
          }
        } catch (Exception e) {
          log.warn("배치 처리 중 오류 발생", e);
        }
      }

      executor.shutdown();

      List<QueryProductMapping> finalMappings =
          queryToProductsMap.entrySet().stream()
              .map(
                  entry ->
                      QueryProductMapping.builder()
                          .query(entry.getKey())
                          .relevantProductIds(String.join(",", entry.getValue()))
                          .build())
              .collect(Collectors.toList());

      queryProductMappingRepository.saveAll(finalMappings);
      log.info("✅ 정답셋 생성 완료: {}개 매핑", finalMappings.size());

    } catch (Exception e) {
      log.error("❌ 정답셋 생성 실패", e);
      throw new RuntimeException("정답셋 생성 중 오류가 발생했습니다", e);
    }
  }

  private List<ProductInfoDto> fetchAllProducts() {
    List<ProductInfoDto> allProducts = new ArrayList<>();
    String scrollId = null;

    try {
      SearchResponse<ProductDocument> initialResponse = initiateScrollSearch();
      scrollId = initialResponse.scrollId();

      List<ProductInfoDto> batch = extractProductInfoFromHits(initialResponse.hits().hits());
      allProducts.addAll(batch);

      while (true) {
        ScrollResponse<ProductDocument> scrollResponse = continueScroll(scrollId);
        if (scrollResponse.hits().hits().isEmpty()) {
          break;
        }
        batch = extractProductInfoFromHits(scrollResponse.hits().hits());
        allProducts.addAll(batch);
        scrollId = scrollResponse.scrollId();
      }

    } catch (Exception e) {
      log.error("❌ Scroll 검색 실패", e);
    } finally {
      if (scrollId != null) {
        clearScroll(scrollId);
      }
    }

    return allProducts;
  }

  private SearchResponse<ProductDocument> initiateScrollSearch() throws Exception {
    SearchRequest searchRequest =
        SearchRequest.of(
            builder ->
                builder
                    .index(ESFields.PRODUCTS_SEARCH_ALIAS)
                    .size(BATCH_SIZE)
                    .scroll(s -> s.time(SCROLL_TIMEOUT))
                    .source(
                        src ->
                            src.filter(
                                f ->
                                    f.includes(
                                        ESFields.PRODUCT_NAME_RAW, ESFields.PRODUCT_SPECS_RAW)))
                    .query(q -> q.term(t -> t.field(ESFields.CATEGORY_NAME).value("SSD"))));

    return elasticsearchClient.search(searchRequest, ProductDocument.class);
  }

  private ScrollResponse<ProductDocument> continueScroll(String scrollId) throws Exception {
    ScrollRequest scrollRequest =
        ScrollRequest.of(builder -> builder.scrollId(scrollId).scroll(s -> s.time(SCROLL_TIMEOUT)));
    return elasticsearchClient.scroll(scrollRequest, ProductDocument.class);
  }

  private void clearScroll(String scrollId) {
    try {
      ClearScrollRequest clearRequest =
          ClearScrollRequest.of(builder -> builder.scrollId(scrollId));
      elasticsearchClient.clearScroll(clearRequest);
    } catch (Exception e) {
      log.warn("⚠️ Scroll 정리 실패", e);
    }
  }

  private List<ProductInfoDto> extractProductInfoFromHits(List<Hit<ProductDocument>> hits) {
    return hits.stream()
        .filter(hit -> hit.source() != null && hit.source().getNameRaw() != null)
        .map(
            hit ->
                new ProductInfoDto(
                    hit.id(),
                    hit.source().getNameRaw().trim(),
                    hit.source().getSpecsRaw() != null ? hit.source().getSpecsRaw().trim() : null))
        .collect(Collectors.toList());
  }

  private List<List<EvaluationQuery>> createQueryBatches(List<EvaluationQuery> queries) {
    List<List<EvaluationQuery>> batches = new ArrayList<>();
    for (int i = 0; i < queries.size(); i += BATCH_SIZE) {
      int end = Math.min(i + BATCH_SIZE, queries.size());
      batches.add(queries.subList(i, end));
    }
    return batches;
  }

  private List<List<ProductInfoDto>> createProductBatches(List<ProductInfoDto> products) {
    List<List<ProductInfoDto>> batches = new ArrayList<>();
    for (int i = 0; i < products.size(); i += BATCH_SIZE) {
      int end = Math.min(i + BATCH_SIZE, products.size());
      batches.add(products.subList(i, end));
    }
    return batches;
  }

  private List<QueryProductMapping> processQueryProductBatch(
      List<EvaluationQuery> queries, List<ProductInfoDto> products) {
    try {
      // LLM API rate limit 방지를 위한 10초 대기
      Thread.sleep(10000);
      log.debug("⏰ Rate limit 방지 대기 완료 (10초)");

      String prompt = buildPrompt(queries, products);
      String llmResponse = llmService.callLLMAPI(prompt);

      saveLLMInteraction(prompt, llmResponse, queries.size(), products.size());

      return parseOneHotResponse(llmResponse, queries, products);
    } catch (InterruptedException e) {
      log.warn("⚠️ 스레드 대기 중단됨", e);
      Thread.currentThread().interrupt();
      return new ArrayList<>();
    } catch (Exception e) {
      log.error("❌ 배치 처리 실패", e);
      return new ArrayList<>();
    }
  }

  private String buildPrompt(List<EvaluationQuery> queries, List<ProductInfoDto> products) {
    String queryList = queries.stream().map(q -> q.getQuery()).collect(Collectors.joining("\n"));

    String productList =
        products.stream().map(p -> formatProductInfo(p)).collect(Collectors.joining("\n"));

    Map<String, String> variables =
        Map.of(
            "QUERY_LIST", queryList,
            "PRODUCT_LIST", productList);

    return promptTemplateLoader.loadTemplate(GROUND_TRUTH_TEMPLATE_FILE, variables);
  }

  private String formatProductInfo(ProductInfoDto product) {
    StringBuilder formatted = new StringBuilder();
    formatted.append(product.getNameRaw());

    if (product.getSpecsRaw() != null && !product.getSpecsRaw().trim().isEmpty()) {
      String specs = product.getSpecsRaw();
      if (specs.length() > 300) {
        specs = specs.substring(0, 300) + "...";
      }
      formatted.append(" | ").append(specs);
    }

    return formatted.toString();
  }

  private List<QueryProductMapping> parseOneHotResponse(
      String response, List<EvaluationQuery> queries, List<ProductInfoDto> products) {
    List<QueryProductMapping> mappings = new ArrayList<>();

    if (response == null || response.trim().isEmpty()) {
      log.error("❌ LLM 응답이 비어있음 - 예상: JSON 형식");
      return mappings;
    }

    try {
      // JSON 응답 파싱
      String cleanedResponse =
          response
              .replaceAll("^```json\\s*", "")
              .replaceAll("^```\\s*", "")
              .replaceAll("\\s*```$", "")
              .trim();

      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode jsonResponse = objectMapper.readTree(cleanedResponse);

      if (!jsonResponse.has("results")) {
        log.error("❌ JSON 응답에 'results' 필드가 없음");
        return mappings;
      }

      JsonNode results = jsonResponse.get("results");

      for (int i = 0; i < results.size(); i++) {
        JsonNode queryResult = results.get(i);
        String queryText = queryResult.get("query").asText();
        JsonNode matches = queryResult.get("matches");

        List<String> relevantProductIds = new ArrayList<>();
        List<String> matchingReasons = new ArrayList<>();

        for (int j = 0; j < matches.size(); j++) {
          JsonNode match = matches.get(j);
          int percentage = match.get("percentage").asInt();
          int productIndex = match.get("product_index").asInt();
          String reason = match.get("reason").asText();

          if (percentage == 100 && productIndex >= 1 && productIndex <= products.size()) {
            String productId = products.get(productIndex - 1).getId();
            relevantProductIds.add(productId);
            matchingReasons.add(productId + ":" + reason);
          }

          log.debug("쿼리: '{}', 상품{}: {}, 이유: {}", queryText, productIndex, percentage, reason);
        }

        if (!relevantProductIds.isEmpty()) {
          String productIdsJson = String.join(",", relevantProductIds);
          String reasonsJson = String.join("|", matchingReasons);

          mappings.add(
              QueryProductMapping.builder()
                  .query(queryText)
                  .relevantProductIds(productIdsJson)
                  .matchingReasons(reasonsJson)
                  .build());
        }
      }

      log.info("✅ JSON 응답 파싱 성공 - {}개 쿼리 중 {}개에서 관련 상품 발견", queries.size(), mappings.size());

    } catch (Exception e) {
      log.error("❌ JSON 파싱 실패", e);
      log.error("응답 내용: {}", response);
    }

    return mappings;
  }

  private void ensureLLMLogDirectory() {
    try {
      Path logDir = Paths.get(LLM_LOG_DIR);
      if (!Files.exists(logDir)) {
        Files.createDirectories(logDir);
        log.info("📁 정답셋 LLM 로그 디렉토리 생성: {}", logDir.toAbsolutePath());
      }
    } catch (IOException e) {
      log.warn("⚠️ 정답셋 LLM 로그 디렉토리 생성 실패", e);
    }
  }

  private void saveLLMInteraction(
      String prompt, String response, int queryCount, int productCount) {
    synchronized (LOG_FILE_LOCK) {
      try {
        String timestamp =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));

        StringBuilder logContent = new StringBuilder();
        logContent.append("\n=== BATCH INTERACTION ===\n");
        logContent.append("Timestamp: ").append(LocalDateTime.now()).append("\n");
        logContent.append("Query Count: ").append(queryCount).append("\n");
        logContent.append("Product Count: ").append(productCount).append("\n");
        logContent
            .append("Expected Response: ")
            .append(queryCount)
            .append(" lines x ")
            .append(productCount)
            .append(" values\n\n");

        logContent.append("=== PROMPT ===\n");
        logContent.append(prompt).append("\n\n");

        logContent.append("=== RESPONSE ===\n");
        logContent.append(response != null ? response : "[NULL_RESPONSE]").append("\n");
        logContent.append("=== END BATCH ===\n\n");

        Path logFile = getLogFilePath();
        Files.writeString(
            logFile, logContent.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        log.debug("💾 배치 LLM 상호작용 추가: q{}xp{}", queryCount, productCount);

      } catch (IOException e) {
        log.warn("⚠️ LLM 상호작용 저장 실패", e);
      }
    }
  }

  private void initializeLogFile() {
    try {
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
      Path logFile = getLogFilePath();

      StringBuilder header = new StringBuilder();
      header.append("=== GROUND TRUTH GENERATION LOG ===\n");
      header.append("Process Started: ").append(LocalDateTime.now()).append("\n");
      header.append("Log File: ").append(logFile.getFileName()).append("\n");
      header.append("Batch Size: ").append(BATCH_SIZE).append("x").append(BATCH_SIZE).append("\n");
      header.append("==========================================\n");

      Files.writeString(
          logFile,
          header.toString(),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
      log.info("📁 통합 LLM 로그 파일 초기화: {}", logFile.getFileName());

    } catch (IOException e) {
      log.warn("⚠️ LLM 로그 파일 초기화 실패", e);
    }
  }

  private Path getLogFilePath() {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String fileName = String.format("ground_truth_full_%s.txt", timestamp);
    return Paths.get(LLM_LOG_DIR, fileName);
  }
}
