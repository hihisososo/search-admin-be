package com.yjlee.search.stats.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.yjlee.search.clicklog.model.ClickLogDocument;
import com.yjlee.search.common.config.BaseIntegrationTest;
import com.yjlee.search.searchlog.model.SearchLogDocument;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class StatsControllerTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ElasticsearchClient elasticsearchClient;

  private static final DateTimeFormatter INDEX_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy.MM.dd");
  private LocalDateTime testTime;
  private String searchLogIndex;
  private String clickLogIndex;

  @BeforeEach
  void setUp() throws Exception {
    testTime = LocalDateTime.now();
    searchLogIndex = "search-logs-" + testTime.format(INDEX_DATE_FORMATTER);
    clickLogIndex = "click-logs-" + testTime.format(INDEX_DATE_FORMATTER);

    // 테스트 데이터 준비 - 검색 로그
    insertSearchLog("노트북", 100L, false, 150L);
    insertSearchLog("키보드", 0L, false, 200L); // 검색 실패
    insertSearchLog("마우스", 50L, false, 100L);
    insertSearchLog("모니터", 30L, true, 0L); // 에러 발생

    // 테스트 데이터 준비 - 클릭 로그
    insertClickLog("노트북", "PROD-12345");
    insertClickLog("노트북", "PROD-67890");
    insertClickLog("마우스", "PROD-11111");

    // Elasticsearch 인덱스 새로고침
    elasticsearchClient.indices().refresh();
  }

  @Test
  @DisplayName("GET /api/v1/stats - 기본 통계 조회")
  void getStats() throws Exception {
    // when & then
    mockMvc
        .perform(get("/api/v1/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalSearchCount").value(4))
        .andExpect(jsonPath("$.totalDocumentCount").value(180)) // 100 + 0 + 50 + 30
        .andExpect(jsonPath("$.searchFailureRate").value(25.0)) // 1/4 * 100
        .andExpect(jsonPath("$.errorCount").value(1))
        .andExpect(jsonPath("$.successRate").value(75.0)) // 3/4 * 100
        .andExpect(jsonPath("$.clickCount").value(3))
        .andExpect(jsonPath("$.clickThroughRate").value(75.0)) // 3/4 * 100
        .andExpect(jsonPath("$.period").exists());
  }

  @Test
  @DisplayName("GET /api/v1/stats - 기간 지정 조회")
  void getStats_WithDateRange() throws Exception {
    // given
    LocalDateTime from = testTime.minusDays(7);
    LocalDateTime to = testTime.plusDays(1);

    // when & then
    mockMvc
        .perform(get("/api/v1/stats").param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalSearchCount").value(4))
        .andExpect(jsonPath("$.clickThroughRate").value(75.0));
  }

  @Test
  @DisplayName("GET /api/v1/stats/popular-keywords - 인기 검색어 조회")
  void getPopularKeywords() throws Exception {
    // when & then
    mockMvc
        .perform(get("/api/v1/stats/popular-keywords").param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keywords").isArray())
        .andExpect(jsonPath("$.keywords[0].keyword").exists())
        .andExpect(jsonPath("$.keywords[0].count").exists())
        .andExpect(jsonPath("$.keywords[0].clickCount").exists())
        .andExpect(jsonPath("$.keywords[0].clickThroughRate").exists())
        .andExpect(jsonPath("$.period").exists());
  }

  @Test
  @DisplayName("GET /api/v1/stats/trends - 시계열 추이 조회")
  void getTrends() throws Exception {
    // when & then
    mockMvc
        .perform(get("/api/v1/stats/trends").param("interval", "hour"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.searchVolumeData").isArray())
        .andExpect(jsonPath("$.period").exists())
        .andExpect(jsonPath("$.interval").value("hour"));
  }

  @Test
  @DisplayName("GET /api/v1/stats/trending-keywords - 급등 검색어 조회")
  void getTrendingKeywords() throws Exception {
    // when & then
    mockMvc
        .perform(get("/api/v1/stats/trending-keywords").param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keywords").isArray())
        .andExpect(jsonPath("$.period").exists());
  }

  private void insertSearchLog(String keyword, Long resultCount, boolean isError, Long responseTime)
      throws Exception {
    SearchLogDocument searchLog =
        SearchLogDocument.builder()
            .timestamp(testTime)
            .searchKeyword(keyword)
            .indexName("products")
            .responseTimeMs(responseTime)
            .resultCount(resultCount)
            .isError(isError)
            .clientIp("127.0.0.1")
            .userAgent("TestAgent")
            .build();

    elasticsearchClient.index(IndexRequest.of(i -> i.index(searchLogIndex).document(searchLog)));
  }

  private void insertClickLog(String keyword, String productId) throws Exception {
    ClickLogDocument clickLog =
        ClickLogDocument.builder()
            .timestamp(testTime)
            .searchKeyword(keyword)
            .clickedProductId(productId)
            .indexName("products")
            .build();

    elasticsearchClient.index(IndexRequest.of(i -> i.index(clickLogIndex).document(clickLog)));
  }
}
