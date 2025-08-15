package com.yjlee.search.loggen.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.search.dto.ProductDto;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoLogGeneratorService {

  private final ElasticsearchClient esClient;
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${server.port:8080}")
  private int serverPort;

  @Value("${app.log-generator.events-per-second:10}")
  private int eventsPerSecond;

  @Value("${app.log-generator.enabled:false}")
  private boolean enabledByConfig;

  private final Random random = new Random();

  @Scheduled(fixedDelay = 1000)
  public void generateLogs() {
    boolean enabled =
        Boolean.parseBoolean(
            System.getProperty("app.log-generator.enabled", String.valueOf(enabledByConfig)));
    if (!enabled) return;

    int count = Math.max(0, eventsPerSecond);
    for (int i = 0; i < count; i++) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  String indexName = ESFields.PRODUCTS_SEARCH_ALIAS;
                  JsonNode randomDoc = getRandomDocument(indexName);

                  if (randomDoc != null) {
                    String keyword = extractKeyword(indexName, randomDoc);

                    searchAndClick(indexName, keyword, randomDoc);
                  }
                } catch (Exception e) {
                  log.error("로그 생성 중 오류 발생", e);
                }
              });
      thread.start();
    }
  }

  private JsonNode getRandomDocument(String indexName) {
    try {
      SearchRequest request =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(1)
                      .query(
                          Query.of(
                              q ->
                                  q.functionScore(
                                      fs ->
                                          fs.query(qb -> qb.matchAll(ma -> ma))
                                              .functions(
                                                  f ->
                                                      f.randomScore(
                                                          rs ->
                                                              rs.field("_seq_no")
                                                                  .seed(
                                                                      String.valueOf(
                                                                          System
                                                                              .currentTimeMillis()))))))));

      SearchResponse<JsonNode> response = esClient.search(request, JsonNode.class);

      if (!response.hits().hits().isEmpty()) {
        return response.hits().hits().get(0).source();
      }
    } catch (Exception e) {
      log.error("랜덤 문서 조회 실패", e);
    }

    return null;
  }

  private String extractKeyword(String indexName, JsonNode doc) {
    String brandName = doc.has(ESFields.BRAND_NAME) ? doc.get(ESFields.BRAND_NAME).asText() : null;
    String categoryName =
        doc.has(ESFields.CATEGORY_NAME) ? doc.get(ESFields.CATEGORY_NAME).asText() : null;
    String productName = doc.has(ESFields.NAME) ? doc.get(ESFields.NAME).asText() : null;

    List<String> tokens = new ArrayList<>();
    if (productName != null && !productName.isBlank()) {
      try {
        tokens = analyzeTokens(indexName, productName);
      } catch (Exception ignore) {
      }
    }

    List<String> models = new ArrayList<>();
    if (doc.has(ESFields.MODEL) && doc.get(ESFields.MODEL).isArray()) {
      for (JsonNode model : doc.get(ESFields.MODEL)) {
        String modelText = model.asText();
        if (!modelText.isEmpty() && !modelText.equals("null")) {
          if ((brandName == null || !modelText.equalsIgnoreCase(brandName))
              && !containsIgnoreCase(models, modelText)) {
            models.add(modelText);
          }
        }
      }
    }

    double rand = random.nextDouble();

    if (rand < 0.3) {
      List<String> singleKeywords = new ArrayList<>();
      if (brandName != null && !brandName.isEmpty()) singleKeywords.add(brandName);
      if (categoryName != null && !categoryName.isEmpty()) singleKeywords.add(categoryName);
      singleKeywords.addAll(tokens);
      singleKeywords.addAll(models);

      if (!singleKeywords.isEmpty()) {
        String k = singleKeywords.get(random.nextInt(singleKeywords.size()));
        return dedupJoin(List.of(k));
      }
    } else if (rand < 0.7) {
      List<String> combinations = new ArrayList<>();

      if (brandName != null && categoryName != null) {
        String k = dedupJoin(List.of(brandName, categoryName));
        if (!k.isBlank()) combinations.add(k);
      }

      if (brandName != null && !tokens.isEmpty()) {
        String k = dedupJoin(List.of(brandName, tokens.get(0)));
        if (!k.isBlank()) combinations.add(k);
      }

      if (categoryName != null && !tokens.isEmpty()) {
        String token = tokens.get(random.nextInt(tokens.size()));
        if (!equalsIgnoreCase(token, categoryName)) {
          String k = dedupJoin(List.of(categoryName, token));
          if (!k.isBlank()) combinations.add(k);
        }
      }

      if (!models.isEmpty() && brandName != null) {
        String k = dedupJoin(List.of(brandName, models.get(0)));
        if (!k.isBlank()) combinations.add(k);
      }

      if (!combinations.isEmpty()) {
        return combinations.get(random.nextInt(combinations.size()));
      }
    } else if (rand < 0.9) {
      List<String> tripleKeywords = new ArrayList<>();

      if (brandName != null && categoryName != null && !tokens.isEmpty()) {
        String k = dedupJoin(List.of(brandName, categoryName, tokens.get(0)));
        if (!k.isBlank()) tripleKeywords.add(k);
      }

      if (brandName != null && !models.isEmpty() && !tokens.isEmpty()) {
        String k = dedupJoin(List.of(brandName, models.get(0), tokens.get(0)));
        if (!k.isBlank()) tripleKeywords.add(k);
      }

      if (tokens.size() >= 3) {
        String k = dedupJoin(List.of(tokens.get(0), tokens.get(1), tokens.get(2)));
        if (!k.isBlank()) tripleKeywords.add(k);
      }

      if (!tripleKeywords.isEmpty()) {
        return tripleKeywords.get(random.nextInt(tripleKeywords.size()));
      }
    } else {
      if (!tokens.isEmpty()) {
        int limit = Math.min(5, tokens.size());
        return dedupJoin(tokens.subList(0, limit));
      }
    }

    if (brandName != null && !brandName.isEmpty()) {
      return dedupJoin(List.of(brandName));
    }
    if (categoryName != null && !categoryName.isEmpty()) {
      return dedupJoin(List.of(categoryName));
    }
    if (!tokens.isEmpty()) {
      return dedupJoin(List.of(tokens.get(0)));
    }

    return "상품";
  }

  private List<String> analyzeTokens(String indexName, String text) throws Exception {
    AnalyzeRequest req =
        AnalyzeRequest.of(a -> a.index(indexName).analyzer("nori_search_analyzer").text(text));
    AnalyzeResponse resp = esClient.indices().analyze(req);
    List<String> out = new ArrayList<>();
    java.util.HashSet<String> seen = new java.util.HashSet<>();
    for (AnalyzeToken t : resp.tokens()) {
      String tok = t.token();
      if (tok == null || tok.isBlank()) continue;
      String key = tok.toLowerCase(java.util.Locale.ROOT);
      if (seen.add(key)) out.add(tok);
    }
    return out;
  }

  private boolean containsIgnoreCase(List<String> list, String value) {
    for (String s : list) {
      if (s != null && value != null && s.equalsIgnoreCase(value)) return true;
    }
    return false;
  }

  private boolean equalsIgnoreCase(String a, String b) {
    if (a == null || b == null) return false;
    return a.equalsIgnoreCase(b);
  }

  private String dedupJoin(List<String> parts) {
    List<String> out = new ArrayList<>();
    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    for (String p : parts) {
      if (p == null) continue;
      String[] toks = p.trim().split("\\s+");
      for (String t : toks) {
        if (t.isBlank()) continue;
        String key = t.toLowerCase(java.util.Locale.ROOT);
        if (!seen.contains(key)) {
          seen.add(key);
          out.add(t);
        }
      }
    }
    return String.join(" ", out);
  }

  private void searchAndClick(String indexName, String keyword, JsonNode originalDoc) {
    try {
      String baseUrl = "http://localhost:" + serverPort;

      String sessionId = UUID.randomUUID().toString();
      String searchUrl =
          baseUrl
              + "/api/v1/search?query="
              + keyword
              + "&size=20&sortField=score&sortOrder=desc&searchSessionId="
              + sessionId;
      ResponseEntity<SearchExecuteResponse> searchResponse =
          restTemplate.getForEntity(searchUrl, SearchExecuteResponse.class);

      if (searchResponse.getStatusCode().is2xxSuccessful()
          && searchResponse.getBody() != null
          && searchResponse.getBody().getHits() != null
          && searchResponse.getBody().getHits().getData() != null
          && !searchResponse.getBody().getHits().getData().isEmpty()) {

        List<ProductDto> products = searchResponse.getBody().getHits().getData();

        // 현실적인 CTR 시뮬레이션 (약 30-40% 클릭률)
        double clickProbability = 0.35;

        // 키워드별로 다른 클릭률 적용
        if (keyword.length() <= 2) {
          clickProbability = 0.25; // 짧은 키워드는 낮은 CTR
        } else if (keyword.contains("브랜드") || keyword.contains("명품")) {
          clickProbability = 0.45; // 브랜드 관련 키워드는 높은 CTR
        }

        // 확률적으로 클릭 여부 결정
        if (random.nextDouble() < clickProbability) {
          // 상위 랭킹 상품을 더 많이 클릭하도록 가중치 적용
          int maxPosition = Math.min(10, products.size());
          int clickPosition = getWeightedRandomPosition(maxPosition);
          ProductDto clickedProduct = products.get(clickPosition);

          ClickLogRequest clickRequest =
              ClickLogRequest.builder()
                  .searchKeyword(keyword)
                  .clickedProductId(clickedProduct.getId())
                  .indexName(ESFields.PRODUCTS_SEARCH_ALIAS)
                  .sessionId(sessionId)
                  .build();

          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.APPLICATION_JSON);
          HttpEntity<ClickLogRequest> entity = new HttpEntity<>(clickRequest, headers);

          String clickUrl = baseUrl + "/api/v1/click-logs";
          restTemplate.postForEntity(clickUrl, entity, Object.class);

          log.info(
              "클릭 로그 생성 - 키워드: {}, 클릭 상품: {}, 순위: {}",
              keyword,
              clickedProduct.getId(),
              clickPosition + 1);
        } else {
          log.info("검색만 수행 (클릭 없음) - 키워드: {}", keyword);
        }
      }
    } catch (Exception e) {
      log.error("검색 및 클릭 로그 생성 실패", e);
    }
  }

  private int getWeightedRandomPosition(int maxPosition) {
    // 상위 순위에 더 높은 가중치 부여
    // 1위: 40%, 2-3위: 30%, 4-6위: 20%, 7-10위: 10%
    double rand = random.nextDouble();
    if (rand < 0.4) {
      return 0; // 1위
    } else if (rand < 0.7 && maxPosition > 1) {
      return 1 + random.nextInt(Math.min(2, maxPosition - 1)); // 2-3위
    } else if (rand < 0.9 && maxPosition > 3) {
      return 3 + random.nextInt(Math.min(3, maxPosition - 3)); // 4-6위
    } else if (maxPosition > 6) {
      return 6 + random.nextInt(maxPosition - 6); // 7위 이하
    }
    return random.nextInt(maxPosition);
  }
}
