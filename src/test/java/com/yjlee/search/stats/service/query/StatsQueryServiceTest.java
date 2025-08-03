package com.yjlee.search.stats.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yjlee.search.stats.domain.SearchStats;
import com.yjlee.search.stats.dto.StatsResponse;
import com.yjlee.search.stats.repository.StatsRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatsQueryServiceTest {

  @Mock
  private StatsRepository statsRepository;

  @InjectMocks
  private StatsQueryService statsQueryService;

  private LocalDateTime from;
  private LocalDateTime to;

  @BeforeEach
  void setUp() {
    from = LocalDateTime.now().minusDays(7);
    to = LocalDateTime.now();
  }

  @Test
  @DisplayName("통계 조회 - 정상 케이스")
  void getStats_Success() {
    // given
    SearchStats searchStats = SearchStats.builder()
        .totalSearchCount(1000L)
        .totalDocumentCount(50000L)
        .searchFailureRate(2.5)
        .errorCount(25L)
        .averageResponseTimeMs(150.5)
        .successRate(97.5)
        .clickCount(300L)
        .clickThroughRate(30.0)
        .build();

    when(statsRepository.getSearchStats(any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(searchStats);

    // when
    StatsResponse response = statsQueryService.getStats(from, to);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getTotalSearchCount()).isEqualTo(1000L);
    assertThat(response.getTotalDocumentCount()).isEqualTo(50000L);
    assertThat(response.getSearchFailureRate()).isEqualTo(2.5);
    assertThat(response.getErrorCount()).isEqualTo(25L);
    assertThat(response.getAverageResponseTimeMs()).isEqualTo(150.5);
    assertThat(response.getSuccessRate()).isEqualTo(97.5);
    assertThat(response.getClickCount()).isEqualTo(300L);
    assertThat(response.getClickThroughRate()).isEqualTo(30.0);
    assertThat(response.getPeriod()).contains(from.toLocalDate().toString());
    assertThat(response.getPeriod()).contains(to.toLocalDate().toString());
  }

  @Test
  @DisplayName("통계 조회 - 데이터가 없는 경우")
  void getStats_NoData() {
    // given
    SearchStats emptyStats = SearchStats.builder()
        .totalSearchCount(0L)
        .totalDocumentCount(0L)
        .searchFailureRate(0.0)
        .errorCount(0L)
        .averageResponseTimeMs(0.0)
        .successRate(0.0)
        .clickCount(0L)
        .clickThroughRate(0.0)
        .build();

    when(statsRepository.getSearchStats(any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(emptyStats);

    // when
    StatsResponse response = statsQueryService.getStats(from, to);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getTotalSearchCount()).isEqualTo(0L);
    assertThat(response.getClickThroughRate()).isEqualTo(0.0);
  }
}