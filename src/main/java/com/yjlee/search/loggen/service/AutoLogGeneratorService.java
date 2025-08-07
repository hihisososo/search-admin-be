package com.yjlee.search.loggen.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.search.dto.ProductDto;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

  private final Random random = new Random();

  @Scheduled(fixedDelay = 1000)
  public void generateLogs() {
    boolean enabled =
        Boolean.parseBoolean(System.getProperty("app.log-generator.enabled", "false"));
    if (!enabled) {
      return;
    }

    for (int i = 0; i < 10; i++) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  String indexName = ESFields.PRODUCTS_SEARCH_ALIAS;
                  JsonNode randomDoc = getRandomDocument(indexName);

                  if (randomDoc != null) {
                    String keyword = extractKeyword(randomDoc);

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

  private String extractKeyword(JsonNode doc) {
    List<String> keywords = new ArrayList<>();

    if (doc.has(ESFields.NAME)) {
      String productName = doc.get(ESFields.NAME).asText();
      String[] words = productName.split("\\s+");
      for (String word : words) {
        if (word.length() > 2) {
          keywords.add(word);
        }
      }
    }

    if (doc.has(ESFields.BRAND_NAME)) {
      keywords.add(doc.get(ESFields.BRAND_NAME).asText());
    }

    if (doc.has(ESFields.CATEGORY_NAME)) {
      keywords.add(doc.get(ESFields.CATEGORY_NAME).asText());
    }

    if (doc.has(ESFields.MODEL) && doc.get(ESFields.MODEL).isArray()) {
      for (JsonNode model : doc.get(ESFields.MODEL)) {
        keywords.add(model.asText());
      }
    }

    if (keywords.isEmpty()) {
      return "상품";
    }

    return keywords.get(random.nextInt(keywords.size()));
  }

  private void searchAndClick(String indexName, String keyword, JsonNode originalDoc) {
    try {
      String baseUrl = "http://localhost:" + serverPort;

      String searchUrl = baseUrl + "/api/v1/search?keyword=" + keyword + "&size=20&sort=RELEVANCE";
      ResponseEntity<SearchExecuteResponse> searchResponse =
          restTemplate.getForEntity(searchUrl, SearchExecuteResponse.class);

      if (searchResponse.getStatusCode().is2xxSuccessful()
          && searchResponse.getBody() != null
          && searchResponse.getBody().getHits() != null
          && searchResponse.getBody().getHits().getData() != null
          && !searchResponse.getBody().getHits().getData().isEmpty()) {

        List<ProductDto> products = searchResponse.getBody().getHits().getData();
        ProductDto clickedProduct = products.get(random.nextInt(Math.min(10, products.size())));

        ClickLogRequest clickRequest =
            ClickLogRequest.builder()
                .searchKeyword(keyword)
                .clickedProductId(clickedProduct.getId())
                .indexName(ESFields.PRODUCTS_SEARCH_ALIAS)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ClickLogRequest> entity = new HttpEntity<>(clickRequest, headers);

        String clickUrl = baseUrl + "/api/v1/click-logs";
        restTemplate.postForEntity(clickUrl, entity, Object.class);

        log.info("로그 생성 완료 - 키워드: {}, 클릭 상품: {}", keyword, clickedProduct.getId());
      }
    } catch (Exception e) {
      log.error("검색 및 클릭 로그 생성 실패", e);
    }
  }
}
