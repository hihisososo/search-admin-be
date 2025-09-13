package com.yjlee.search.clicklog.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.clicklog.dto.ClickLogResponse;
import com.yjlee.search.clicklog.model.ClickLogDocument;
import com.yjlee.search.common.config.LocalElasticsearchTest;
import com.yjlee.search.common.constants.IndexNameConstants;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ClickLogIntegrationTest extends LocalElasticsearchTest {

  @Autowired private ClickLogService clickLogService;

  private static final DateTimeFormatter INDEX_DATE_FORMATTER =
      DateTimeFormatter.ofPattern(IndexNameConstants.INDEX_DATE_FORMAT);

  @BeforeEach
  void setUp() throws Exception {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String indexName = IndexNameConstants.CLICK_LOG_PREFIX + now.format(INDEX_DATE_FORMATTER);
    deleteIndex(indexName);
  }

  @Test
  @DisplayName("클릭 로그 저장")
  void saveClickLogToElasticsearch() throws Exception {
    ClickLogRequest request =
        ClickLogRequest.builder()
            .searchKeyword("노트북")
            .clickedProductId("PROD-12345")
            .indexName("products")
            .sessionId(UUID.randomUUID().toString())
            .build();

    ClickLogResponse response = clickLogService.logClick(request);

    assertThat(response.isSuccess()).isTrue();

    Thread.sleep(1000);

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String indexName = IndexNameConstants.CLICK_LOG_PREFIX + now.format(INDEX_DATE_FORMATTER);

    CountRequest countRequest = CountRequest.of(c -> c.index(indexName));
    CountResponse countResponse = elasticsearchClient.count(countRequest);

    assertThat(countResponse.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("키워드로 검색")
  void searchClickLogByKeyword() throws Exception {
    String keyword = "갤럭시북";
    String productId = "PROD-GALAXY-001";

    ClickLogRequest request =
        ClickLogRequest.builder()
            .searchKeyword(keyword)
            .clickedProductId(productId)
            .indexName("products")
            .build();

    clickLogService.logClick(request);

    Thread.sleep(1000);

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String indexName = IndexNameConstants.CLICK_LOG_PREFIX + now.format(INDEX_DATE_FORMATTER);

    SearchRequest searchRequest =
        SearchRequest.of(
            s ->
                s.index(indexName)
                    .query(Query.of(q -> q.match(m -> m.field("search_keyword").query(keyword)))));

    SearchResponse<ClickLogDocument> searchResponse =
        elasticsearchClient.search(searchRequest, ClickLogDocument.class);

    assertThat(searchResponse.hits().total().value()).isEqualTo(1);
    ClickLogDocument document = searchResponse.hits().hits().get(0).source();
    assertThat(document.getSearchKeyword()).isEqualTo(keyword);
    assertThat(document.getClickedProductId()).isEqualTo(productId);
  }

  @Test
  @DisplayName("대량 동시 저장")
  void saveBulkClickLogs() throws Exception {
    List<CompletableFuture<ClickLogResponse>> futures = new ArrayList<>();
    int logCount = 50;

    for (int i = 0; i < logCount; i++) {
      ClickLogRequest request =
          ClickLogRequest.builder()
              .searchKeyword("키워드" + i)
              .clickedProductId("PROD-" + i)
              .indexName("products")
              .sessionId(UUID.randomUUID().toString())
              .build();

      CompletableFuture<ClickLogResponse> future =
          CompletableFuture.supplyAsync(() -> clickLogService.logClick(request));
      futures.add(future);
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

    List<ClickLogResponse> responses = futures.stream().map(CompletableFuture::join).toList();

    assertThat(responses).hasSize(logCount);
    assertThat(responses).allMatch(ClickLogResponse::isSuccess);

    Thread.sleep(2000);

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String indexName = IndexNameConstants.CLICK_LOG_PREFIX + now.format(INDEX_DATE_FORMATTER);

    CountRequest countRequest = CountRequest.of(c -> c.index(indexName));
    CountResponse countResponse = elasticsearchClient.count(countRequest);

    assertThat(countResponse.count()).isEqualTo(logCount);
  }

  @Test
  @DisplayName("인덱스 자동 생성")
  void verifyIndexAutoCreation() throws Exception {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String expectedIndexName =
        IndexNameConstants.CLICK_LOG_PREFIX + now.format(INDEX_DATE_FORMATTER);

    assertThat(indexExists(expectedIndexName)).isFalse();

    ClickLogRequest request =
        ClickLogRequest.builder()
            .searchKeyword("테스트")
            .clickedProductId("PROD-TEST")
            .indexName("products")
            .build();

    clickLogService.logClick(request);

    Thread.sleep(1000);

    assertThat(indexExists(expectedIndexName)).isTrue();
  }

  @Test
  @DisplayName("세션별 조회")
  void searchClickLogsBySession() throws Exception {
    String sessionId = UUID.randomUUID().toString();
    String[] keywords = {"노트북", "마우스", "키보드"};

    for (String keyword : keywords) {
      ClickLogRequest request =
          ClickLogRequest.builder()
              .searchKeyword(keyword)
              .clickedProductId("PROD-" + keyword.hashCode())
              .indexName("products")
              .sessionId(sessionId)
              .build();

      clickLogService.logClick(request);
    }

    Thread.sleep(2000);

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String indexName = IndexNameConstants.CLICK_LOG_PREFIX + now.format(INDEX_DATE_FORMATTER);

    SearchRequest searchRequest =
        SearchRequest.of(
            s ->
                s.index(indexName)
                    .query(
                        Query.of(q -> q.term(t -> t.field("session_id.keyword").value(sessionId))))
                    .size(10));

    SearchResponse<ClickLogDocument> searchResponse =
        elasticsearchClient.search(searchRequest, ClickLogDocument.class);

    assertThat(searchResponse.hits().total().value()).isEqualTo(3);

    List<String> foundKeywords =
        searchResponse.hits().hits().stream().map(hit -> hit.source().getSearchKeyword()).toList();

    assertThat(foundKeywords).containsExactlyInAnyOrder(keywords);
  }

  @Test
  @DisplayName("상품별 클릭 빈도")
  void countClicksByProduct() throws Exception {
    String targetProductId = "PROD-POPULAR";

    for (int i = 0; i < 5; i++) {
      ClickLogRequest request =
          ClickLogRequest.builder()
              .searchKeyword("인기상품")
              .clickedProductId(targetProductId)
              .indexName("products")
              .sessionId(UUID.randomUUID().toString())
              .build();

      clickLogService.logClick(request);
    }

    for (int i = 0; i < 3; i++) {
      ClickLogRequest request =
          ClickLogRequest.builder()
              .searchKeyword("다른상품")
              .clickedProductId("PROD-OTHER-" + i)
              .indexName("products")
              .build();

      clickLogService.logClick(request);
    }

    Thread.sleep(2000);

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String indexName = IndexNameConstants.CLICK_LOG_PREFIX + now.format(INDEX_DATE_FORMATTER);

    CountRequest countRequest =
        CountRequest.of(
            c ->
                c.index(indexName)
                    .query(
                        Query.of(
                            q ->
                                q.term(
                                    t ->
                                        t.field("clicked_product_id.keyword")
                                            .value(targetProductId)))));

    CountResponse countResponse = elasticsearchClient.count(countRequest);

    assertThat(countResponse.count()).isEqualTo(5);
  }
}
