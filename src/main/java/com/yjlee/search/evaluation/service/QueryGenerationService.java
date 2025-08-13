package com.yjlee.search.evaluation.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.util.PromptTemplateLoader;
import com.yjlee.search.evaluation.dto.ProductInfoDto;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.index.dto.ProductDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryGenerationService {

  private final ElasticsearchClient elasticsearchClient;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final LLMService llmService;
  private final PromptTemplateLoader promptTemplateLoader;

  public List<String> generateRandomQueries(int count) {
    try {
      log.info("랜덤 쿼리 생성 시작: {}개", count);

      Set<String> existingQueries = new HashSet<>(evaluationQueryRepository.findAllQueryStrings());
      Set<String> generatedQueries = new HashSet<>();
      int attempts = 0;
      int maxAttempts = count * 5;

      while (generatedQueries.size() < count && attempts < maxAttempts) {
        int batchSize = Math.min(20, count - generatedQueries.size());
        List<ProductInfoDto> products = fetchRandomProducts(batchSize * 2);

        if (!products.isEmpty()) {
          try {
            String prompt = buildBulkQueryPrompt(products);
            String response = llmService.callLLMAPI(prompt);
            List<String> batchQueries = extractQueriesFromBulkResponse(response);

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
          } catch (Exception e) {
            log.warn("⚠️ 벌크 쿼리 생성 중 오류 발생", e);
          }
        }

        attempts++;
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
      int attempts = 0;
      int maxAttempts = count * 5;

      while (generated.size() < count && attempts < maxAttempts) {
        int batchSize = 20;
        List<ProductInfoDto> products = fetchRandomProducts(batchSize);

        if (!products.isEmpty()) {
          try {
            String prompt = buildBulkQueryPrompt(products);
            String response = llmService.callLLMAPI(prompt);
            List<String> batchQueries = extractQueriesFromBulkResponse(response);

            for (String q : batchQueries) {
              if (q != null && !q.trim().isEmpty() && isValidQuery(q) && !generated.contains(q)) {
                generated.add(q.trim());
                if (generated.size() >= count) break;
              }
            }
          } catch (Exception e) {
            log.warn("[PREVIEW] 벌크 쿼리 생성 중 오류", e);
          }
        }
        attempts++;
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
      int attempts = 0;
      int maxAttempts = count * 5;

      while (generated.size() < count && attempts < maxAttempts) {
        int batchSize = 20;
        List<ProductInfoDto> products = fetchRandomProductsByCategory(batchSize, category);

        if (!products.isEmpty()) {
          try {
            String prompt = buildBulkQueryPrompt(products);
            String response = llmService.callLLMAPI(prompt);
            List<String> batchQueries = extractQueriesFromBulkResponse(response);

            for (String q : batchQueries) {
              if (q != null && !q.trim().isEmpty() && isValidQuery(q) && !generated.contains(q)) {
                generated.add(q.trim());
                if (generated.size() >= count) break;
              }
            }
          } catch (Exception e) {
            log.warn("[PREVIEW] 벌크 쿼리 생성(카테고리) 오류", e);
          }
        }
        attempts++;
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
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(ESFields.PRODUCTS_SEARCH_ALIAS)
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
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(ESFields.PRODUCTS_SEARCH_ALIAS)
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

    StringBuilder productList = new StringBuilder();
    for (ProductInfoDto product : products) {
      if (isValidProductInfo(product)) {
        productList.append(String.format("%s | %s\n", product.getNameRaw(), product.getSpecsRaw()));
      }
    }

    return template.replace("{PRODUCT_LIST}", productList.toString().trim());
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
              .map(query -> EvaluationQuery.builder().query(query).count(1).build())
              .collect(Collectors.toList());

      evaluationQueryRepository.saveAll(evaluationQueries);
      log.info("쿼리 저장 완료: {}개", newQueries.size());
    } else {
      log.info("저장할 새로운 쿼리가 없습니다");
    }
  }
}
