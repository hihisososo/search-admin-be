package com.yjlee.search.evaluation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.evaluation.dto.GenerateQueryRequest;
import com.yjlee.search.evaluation.dto.ProductInfoDto;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryGenerationService {

  private static final int BATCH_SIZE = 50;
  private static final int SPECS_MAX_LENGTH = 300;
  private static final String QUERY_TEMPLATE_FILE = "query-generation.txt";
  private static final String SCROLL_TIMEOUT = "5m";
  private static final String LLM_LOG_DIR = "logs/llm";

  private final ElasticsearchClient elasticsearchClient;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final LLMService llmService;
  private final PromptTemplateLoader promptTemplateLoader;

  public void generateCandidateQueries(GenerateQueryRequest request) {
    try {
      log.info("🚀 쿼리 생성 시작");
      ensureLLMLogDirectory();

      List<ProductInfoDto> allProducts = fetchAllProducts();
      log.info("📊 Scroll API로 상품 조회 완료: {}개", allProducts.size());

      Map<String, Integer> queryCountMap = new HashMap<>();
      List<List<ProductInfoDto>> batches = new ArrayList<>();
      for (int i = 0; i < allProducts.size(); i += BATCH_SIZE) {
        int end = Math.min(i + BATCH_SIZE, allProducts.size());
        batches.add(allProducts.subList(i, end));
      }

      var executor = Executors.newFixedThreadPool(10);
      List<Future<Map<String, Integer>>> futures = new ArrayList<>();
      for (List<ProductInfoDto> batch : batches) {
        futures.add(executor.submit(() -> processProductsInBatches(batch)));
      }
      for (var f : futures) {
        try {
          var result = f.get();
          result.forEach((k, v) -> queryCountMap.merge(k, v, Integer::sum));
        } catch (Exception e) {
          log.warn("쿼리 생성 중 일부 배치 처리 실패", e);
        }
      }
      executor.shutdown();
      log.info("🔍 쿼리 생성 완료: {}개 고유 쿼리", queryCountMap.size());

      saveGeneratedQueries(queryCountMap);
      log.info("✅ 쿼리 저장 완료");

    } catch (Exception e) {
      log.error("❌ 쿼리 생성 실패", e);
      throw new RuntimeException("쿼리 생성 중 오류가 발생했습니다", e);
    }
  }

  private void ensureLLMLogDirectory() {
    try {
      Path logDir = Paths.get(LLM_LOG_DIR);
      if (!Files.exists(logDir)) {
        Files.createDirectories(logDir);
        log.info("📁 LLM 로그 디렉토리 생성: {}", logDir.toAbsolutePath());
      }
    } catch (IOException e) {
      log.warn("⚠️ LLM 로그 디렉토리 생성 실패", e);
    }
  }

  private List<ProductInfoDto> fetchAllProducts() {
    List<ProductInfoDto> allProducts = new ArrayList<>();
    String scrollId = null;

    try {
      // 첫 번째 scroll 검색 요청
      SearchResponse<ProductDocument> initialResponse = initiateScrollSearch();
      scrollId = initialResponse.scrollId();

      // 첫 번째 배치 처리
      List<ProductInfoDto> batch = extractProductInfoFromHits(initialResponse.hits().hits());
      allProducts.addAll(batch);
      log.info("📊 첫 번째 배치 조회: {}개", batch.size());

      // scroll을 통해 나머지 배치들 처리
      while (true) {
        ScrollResponse<ProductDocument> scrollResponse = continueScroll(scrollId);

        if (scrollResponse.hits().hits().isEmpty()) {
          break;
        }

        batch = extractProductInfoFromHits(scrollResponse.hits().hits());
        allProducts.addAll(batch);
        log.debug("📊 배치 추가 조회: {}개 (총 {}개)", batch.size(), allProducts.size());

        scrollId = scrollResponse.scrollId();
      }

    } catch (Exception e) {
      log.error("❌ Scroll 검색 실패", e);
    } finally {
      // scroll 정리
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

    log.debug("🔍 Scroll 검색 시작: batch_size={}, timeout={}", BATCH_SIZE, SCROLL_TIMEOUT);
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
      log.debug("🧹 Scroll 정리 완료: scrollId={}", scrollId);
    } catch (Exception e) {
      log.warn("⚠️ Scroll 정리 실패: scrollId={}", scrollId, e);
    }
  }

  private List<ProductInfoDto> extractProductInfoFromHits(List<Hit<ProductDocument>> hits) {
    return hits.stream()
        .filter(hit -> isValidProduct(hit.source()))
        .map(
            hit ->
                new ProductInfoDto(
                    hit.id(), hit.source().getNameRaw().trim(), hit.source().getSpecsRaw()))
        .collect(Collectors.toList());
  }

  private boolean isValidProduct(ProductDocument product) {
    return product != null
        && product.getNameRaw() != null
        && !product.getNameRaw().trim().isEmpty();
  }

  private Map<String, Integer> processProductsInBatches(List<ProductInfoDto> allProducts) {
    List<List<ProductInfoDto>> batches = createBatches(allProducts);
    Map<String, Integer> aggregatedQueries = new HashMap<>();

    for (int i = 0; i < batches.size(); i++) {
      Map<String, Integer> batchQueries = processSingleBatch(batches.get(i), i + 1, batches.size());
      mergeQueryCounts(aggregatedQueries, batchQueries);
    }

    return aggregatedQueries;
  }

  private List<List<ProductInfoDto>> createBatches(List<ProductInfoDto> products) {
    List<List<ProductInfoDto>> batches = new ArrayList<>();
    for (int i = 0; i < products.size(); i += BATCH_SIZE) {
      int endIndex = Math.min(i + BATCH_SIZE, products.size());
      batches.add(products.subList(i, endIndex));
    }
    return batches;
  }

  private Map<String, Integer> processSingleBatch(
      List<ProductInfoDto> batch, int batchNumber, int totalBatches) {
    try {
      log.debug("🔄 배치 처리 중: {}/{} (상품 {}개)", batchNumber, totalBatches, batch.size());
      return generateQueriesFromLLM(batch);
    } catch (Exception e) {
      log.error("❌ 배치 {} 처리 실패", batchNumber, e);
      return Collections.emptyMap();
    }
  }

  private Map<String, Integer> generateQueriesFromLLM(List<ProductInfoDto> products) {
    String prompt = buildPromptForProducts(products);

    // LLM API 호출
    String llmResponse = llmService.callLLMAPI(prompt);

    // 입력/출력 파일로 저장
    saveLLMInteraction(prompt, llmResponse, products.size());

    if (isEmptyResponse(llmResponse)) {
      log.warn("⚠️ LLM 응답이 비어있음");
      return Collections.emptyMap();
    }

    return parseQueriesFromResponse(llmResponse, products.size());
  }

  private void saveLLMInteraction(String prompt, String response, int productCount) {
    try {
      String timestamp =
          LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
      String baseFileName = String.format("batch_%s_products_%d", timestamp, productCount);

      // 입력 프롬프트 저장
      Path promptFile = Paths.get(LLM_LOG_DIR, baseFileName + "_prompt.txt");
      Files.writeString(promptFile, prompt, StandardOpenOption.CREATE);

      // 출력 응답 저장
      Path responseFile = Paths.get(LLM_LOG_DIR, baseFileName + "_response.txt");
      Files.writeString(
          responseFile, response != null ? response : "[NULL_RESPONSE]", StandardOpenOption.CREATE);

      log.debug(
          "💾 LLM 상호작용 저장: prompt={}, response={}",
          promptFile.getFileName(),
          responseFile.getFileName());

    } catch (IOException e) {
      log.warn("⚠️ LLM 상호작용 저장 실패", e);
    }
  }

  private String buildPromptForProducts(List<ProductInfoDto> products) {
    StringBuilder productListText = new StringBuilder();
    for (int i = 0; i < products.size(); i++) {
      productListText.append(String.format("%d. %s", i, formatProductForPrompt(products.get(i))));
      if (i < products.size() - 1) {
        productListText.append("\n");
      }
    }

    Map<String, String> variables = Map.of("PRODUCT_LIST", productListText.toString());
    return promptTemplateLoader.loadTemplate(QUERY_TEMPLATE_FILE, variables);
  }

  private String formatProductForPrompt(ProductInfoDto product) {
    StringBuilder formatted = new StringBuilder();
    formatted.append(String.format("상품명: %s", product.getNameRaw()));

    if (hasValidSpecs(product)) {
      String specs = truncateSpecs(product.getSpecsRaw());
      formatted.append(String.format("\n   스펙: %s", specs));
    }

    return formatted.toString();
  }

  private boolean hasValidSpecs(ProductInfoDto product) {
    return product.getSpecsRaw() != null && !product.getSpecsRaw().trim().isEmpty();
  }

  private String truncateSpecs(String specs) {
    return specs.length() > SPECS_MAX_LENGTH ? specs.substring(0, SPECS_MAX_LENGTH) + "..." : specs;
  }

  private boolean isEmptyResponse(String response) {
    return response == null || response.trim().isEmpty();
  }

  private Map<String, Integer> parseQueriesFromResponse(String response, int expectedProductCount) {
    try {
      Map<String, Integer> queryCount = new HashMap<>();
      String[] lines = response.split("\\r?\\n");

      int processedLines = 0;
      for (String line : lines) {
        if (line.trim().isEmpty() || processedLines >= expectedProductCount) {
          continue;
        }

        List<String> queries = extractQueriesFromLine(line);
        queries.forEach(query -> queryCount.merge(query, 1, Integer::sum));

        processedLines++;
      }

      return queryCount;
    } catch (Exception e) {
      log.error("❌ LLM 응답 파싱 실패", e);
      return Collections.emptyMap();
    }
  }

  private List<String> extractQueriesFromLine(String line) {
    return Arrays.stream(line.split("\\t"))
        .map(String::trim)
        .filter(query -> !query.isEmpty())
        .collect(Collectors.toList());
  }

  private void mergeQueryCounts(Map<String, Integer> target, Map<String, Integer> source) {
    source.forEach((query, count) -> target.merge(query, count, Integer::sum));
  }

  private void saveGeneratedQueries(Map<String, Integer> queryCountMap) {
    List<EvaluationQuery> evaluationQueries =
        queryCountMap.entrySet().stream()
            .map(this::createEvaluationQuery)
            .collect(Collectors.toList());

    evaluationQueryRepository.saveAll(evaluationQueries);
  }

  private EvaluationQuery createEvaluationQuery(Map.Entry<String, Integer> entry) {
    return EvaluationQuery.builder().query(entry.getKey()).count(entry.getValue()).build();
  }
}
