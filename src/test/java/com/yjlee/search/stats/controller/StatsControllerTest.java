package com.yjlee.search.stats.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yjlee.search.stats.dto.PopularKeywordResponse;
import com.yjlee.search.stats.dto.StatsResponse;
import com.yjlee.search.stats.dto.TrendResponse;
import com.yjlee.search.stats.service.StatsService;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private StatsService statsService;

  private StatsResponse statsResponse;
  private PopularKeywordResponse popularKeywordResponse;
  private TrendResponse trendResponse;

  @BeforeEach
  void setUp() {
    // Stats Response 준비
    statsResponse = StatsResponse.builder()
        .totalSearchCount(1000L)
        .totalDocumentCount(50000L)
        .searchFailureRate(2.5)
        .errorCount(25L)
        .averageResponseTimeMs(150.5)
        .successRate(97.5)
        .clickCount(300L)
        .clickThroughRate(30.0)
        .period("2024-01-01 ~ 2024-01-08")
        .build();

    // Popular Keyword Response 준비
    popularKeywordResponse = PopularKeywordResponse.builder()
        .keywords(Arrays.asList(
            PopularKeywordResponse.KeywordStats.builder()
                .keyword("노트북")
                .count(100L)
                .clickCount(30L)
                .clickThroughRate(30.0)
                .percentage(50.0)
                .rank(1)
                .changeStatus(PopularKeywordResponse.RankChangeStatus.UP)
                .build(),
            PopularKeywordResponse.KeywordStats.builder()
                .keyword("키보드")
                .count(80L)
                .clickCount(20L)
                .clickThroughRate(25.0)
                .percentage(40.0)
                .rank(2)
                .changeStatus(PopularKeywordResponse.RankChangeStatus.DOWN)
                .build()
        ))
        .period("2024-01-01 ~ 2024-01-08")
        .build();

    // Trend Response 준비
    trendResponse = TrendResponse.builder()
        .searchVolumeData(Arrays.asList(
            TrendResponse.TrendData.builder()
                .timestamp(LocalDateTime.now().minusDays(1))
                .searchCount(100L)
                .clickCount(30L)
                .clickThroughRate(30.0)
                .averageResponseTime(150.0)
                .label("2024-01-07")
                .build(),
            TrendResponse.TrendData.builder()
                .timestamp(LocalDateTime.now())
                .searchCount(120L)
                .clickCount(40L)
                .clickThroughRate(33.3)
                .averageResponseTime(140.0)
                .label("2024-01-08")
                .build()
        ))
        .responseTimeData(Arrays.asList())
        .period("2024-01-01 ~ 2024-01-08")
        .interval("day")
        .build();
  }

  @Test
  @DisplayName("GET /api/v1/stats - 기본 통계 조회")
  void getStats() throws Exception {
    // given
    when(statsService.getStats(any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(statsResponse);

    // when & then
    mockMvc.perform(get("/api/v1/stats")
            .param("from", "2024-01-01T00:00:00")
            .param("to", "2024-01-08T00:00:00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalSearchCount").value(1000))
        .andExpect(jsonPath("$.totalDocumentCount").value(50000))
        .andExpect(jsonPath("$.searchFailureRate").value(2.5))
        .andExpect(jsonPath("$.errorCount").value(25))
        .andExpect(jsonPath("$.averageResponseTimeMs").value(150.5))
        .andExpect(jsonPath("$.successRate").value(97.5))
        .andExpect(jsonPath("$.clickCount").value(300))
        .andExpect(jsonPath("$.clickThroughRate").value(30.0))
        .andExpect(jsonPath("$.period").value("2024-01-01 ~ 2024-01-08"));
  }

  @Test
  @DisplayName("GET /api/v1/stats - 파라미터 없이 조회 (기본값 사용)")
  void getStats_WithoutParams() throws Exception {
    // given
    when(statsService.getStats(any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(statsResponse);

    // when & then
    mockMvc.perform(get("/api/v1/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalSearchCount").exists())
        .andExpect(jsonPath("$.clickThroughRate").exists());
  }

  @Test
  @DisplayName("GET /api/v1/stats/popular-keywords - 인기 검색어 조회")
  void getPopularKeywords() throws Exception {
    // given
    when(statsService.getPopularKeywords(any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
        .thenReturn(popularKeywordResponse);

    // when & then
    mockMvc.perform(get("/api/v1/stats/popular-keywords")
            .param("from", "2024-01-01T00:00:00")
            .param("to", "2024-01-08T00:00:00")
            .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keywords").isArray())
        .andExpect(jsonPath("$.keywords[0].keyword").value("노트북"))
        .andExpect(jsonPath("$.keywords[0].count").value(100))
        .andExpect(jsonPath("$.keywords[0].clickCount").value(30))
        .andExpect(jsonPath("$.keywords[0].clickThroughRate").value(30.0))
        .andExpect(jsonPath("$.keywords[0].rank").value(1))
        .andExpect(jsonPath("$.keywords[0].changeStatus").value("UP"))
        .andExpect(jsonPath("$.period").value("2024-01-01 ~ 2024-01-08"));
  }

  @Test
  @DisplayName("GET /api/v1/stats/trends - 시계열 추이 조회")
  void getTrends() throws Exception {
    // given
    when(statsService.getTrends(any(LocalDateTime.class), any(LocalDateTime.class), anyString()))
        .thenReturn(trendResponse);

    // when & then
    mockMvc.perform(get("/api/v1/stats/trends")
            .param("from", "2024-01-01T00:00:00")
            .param("to", "2024-01-08T00:00:00")
            .param("interval", "day"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.searchVolumeData").isArray())
        .andExpect(jsonPath("$.searchVolumeData[0].searchCount").value(100))
        .andExpect(jsonPath("$.searchVolumeData[0].clickCount").value(30))
        .andExpect(jsonPath("$.searchVolumeData[0].clickThroughRate").value(30.0))
        .andExpect(jsonPath("$.searchVolumeData[0].label").value("2024-01-07"))
        .andExpect(jsonPath("$.period").value("2024-01-01 ~ 2024-01-08"))
        .andExpect(jsonPath("$.interval").value("day"));
  }

  @Test
  @DisplayName("GET /api/v1/stats/trending-keywords - 급등 검색어 조회")
  void getTrendingKeywords() throws Exception {
    // given
    PopularKeywordResponse trendingResponse = PopularKeywordResponse.builder()
        .keywords(Arrays.asList(
            PopularKeywordResponse.KeywordStats.builder()
                .keyword("아이폰15")
                .count(200L)
                .clickCount(100L)
                .clickThroughRate(50.0)
                .percentage(60.0)
                .rank(1)
                .build()
        ))
        .period("2024-01-01 ~ 2024-01-08")
        .build();

    when(statsService.getTrendingKeywords(any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
        .thenReturn(trendingResponse);

    // when & then
    mockMvc.perform(get("/api/v1/stats/trending-keywords")
            .param("from", "2024-01-01T00:00:00")
            .param("to", "2024-01-08T00:00:00")
            .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keywords").isArray())
        .andExpect(jsonPath("$.keywords[0].keyword").value("아이폰15"))
        .andExpect(jsonPath("$.keywords[0].clickThroughRate").value(50.0));
  }
}