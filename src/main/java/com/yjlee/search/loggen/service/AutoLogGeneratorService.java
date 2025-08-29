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
import java.util.Arrays;
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
    // 1. 기본 데이터 추출
    String brand = doc.has(ESFields.BRAND_NAME) ? doc.get(ESFields.BRAND_NAME).asText() : null;
    String category =
        doc.has(ESFields.CATEGORY_NAME) ? doc.get(ESFields.CATEGORY_NAME).asText() : null;
    String productName = doc.has(ESFields.NAME) ? doc.get(ESFields.NAME).asText() : null;

    // 2. 토큰과 모델 정보 추출
    List<String> nameTokens = new ArrayList<>();
    if (productName != null && !productName.isBlank()) {
      try {
        nameTokens = analyzeTokens(indexName, productName);
      } catch (Exception ignore) {
      }
    }

    List<String> models = extractModels(doc, brand);

    // 3. 실제 사용자 검색 패턴 생성
    List<String> searchPatterns = new ArrayList<>();

    // 브랜드 + 카테고리 (예: "삼성 냉장고")
    if (brand != null && category != null) {
      searchPatterns.add(formatKeyword(brand, category));
    }

    // 브랜드 + 제품명 토큰 (예: "LG 그램")
    if (brand != null && !nameTokens.isEmpty()) {
      String token = pickRandom(nameTokens);
      if (token != null && !equalsIgnoreCase(token, brand)) {
        searchPatterns.add(formatKeyword(brand, token));
      }
    }

    // 카테고리 + 제품특징 (예: "노트북 게이밍")
    if (category != null && !nameTokens.isEmpty()) {
      String token = pickRandom(nameTokens);
      if (token != null && !equalsIgnoreCase(token, category)) {
        searchPatterns.add(formatKeyword(category, token));
      }
    }

    // 브랜드 + 모델명 (예: "삼성 RF85R")
    if (brand != null && !models.isEmpty()) {
      searchPatterns.add(formatKeyword(brand, models.get(0)));
    }

    // 제품명 주요 토큰 조합 (예: "비스포크 4도어 냉장고")
    if (nameTokens.size() >= 2) {
      int maxTokens = Math.min(4, nameTokens.size());
      int tokenCount = 2 + random.nextInt(maxTokens - 1);
      searchPatterns.add(joinTokens(nameTokens, tokenCount));
    }

    // 4. 유효한 패턴 중 랜덤 선택 (한 단어 제외)
    List<String> validPatterns =
        searchPatterns.stream()
            .filter(p -> p != null && !p.isBlank())
            .filter(p -> p.split("\\s+").length >= 2) // 최소 2단어 이상
            .distinct()
            .collect(java.util.stream.Collectors.toList());

    if (!validPatterns.isEmpty()) {
      return validPatterns.get(random.nextInt(validPatterns.size()));
    }

    // 5. Fallback: 카테고리 + "추천"
    if (category != null) {
      return category + " 추천";
    }

    return "상품 추천";
  }

  private List<String> extractModels(JsonNode doc, String brand) {
    List<String> models = new ArrayList<>();
    if (doc.has(ESFields.MODEL) && doc.get(ESFields.MODEL).isArray()) {
      for (JsonNode model : doc.get(ESFields.MODEL)) {
        String modelText = model.asText();
        if (!modelText.isEmpty() && !modelText.equals("null")) {
          if ((brand == null || !modelText.equalsIgnoreCase(brand))) {
            models.add(modelText);
          }
        }
      }
    }
    return models;
  }

  private String pickRandom(List<String> items) {
    if (items == null || items.isEmpty()) return null;
    return items.get(random.nextInt(items.size()));
  }

  private String formatKeyword(String... parts) {
    if (parts == null || parts.length == 0) return null;
    return dedupJoin(Arrays.asList(parts));
  }

  private String joinTokens(List<String> tokens, int count) {
    if (tokens == null || tokens.size() < count) return null;
    return dedupJoin(tokens.subList(0, count));
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
