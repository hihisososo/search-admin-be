package com.yjlee.search.stats.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.yjlee.search.stats.domain.KeywordStats;
import com.yjlee.search.stats.dto.PopularKeywordResponse;
import com.yjlee.search.stats.repository.StatsRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopularKeywordQueryServiceTest {

  @Mock private StatsRepository statsRepository;

  @InjectMocks private PopularKeywordQueryService popularKeywordQueryService;

  private LocalDateTime from;
  private LocalDateTime to;

  @BeforeEach
  void setUp() {
    from = LocalDateTime.now().minusDays(7);
    to = LocalDateTime.now();
  }

  @Test
  @DisplayName("인기 검색어 조회 - 순위 변동 계산")
  void getPopularKeywords_WithRankChanges() {
    // given
    // ElasticsearchStatsRepository는 한 번의 호출로 순위 변동 정보가 포함된 데이터를 반환
    List<KeywordStats> keywordsWithRankChanges =
        Arrays.asList(
            KeywordStats.builder()
                .keyword("노트북")
                .searchCount(100L)
                .clickCount(30L)
                .clickThroughRate(30.0)
                .percentage(50.0)
                .rank(1)
                .previousRank(2) // 이전 순위
                .rankChange(1) // 순위 변동 (2->1, 상승)
                .changeStatus(KeywordStats.RankChangeStatus.UP)
                .build(),
            KeywordStats.builder()
                .keyword("키보드")
                .searchCount(80L)
                .clickCount(20L)
                .clickThroughRate(25.0)
                .percentage(40.0)
                .rank(2)
                .previousRank(1) // 이전 순위
                .rankChange(-1) // 순위 변동 (1->2, 하락)
                .changeStatus(KeywordStats.RankChangeStatus.DOWN)
                .build(),
            KeywordStats.builder()
                .keyword("마우스")
                .searchCount(20L)
                .clickCount(5L)
                .clickThroughRate(25.0)
                .percentage(10.0)
                .rank(3)
                .previousRank(null) // 신규 진입
                .rankChange(null)
                .changeStatus(KeywordStats.RankChangeStatus.NEW)
                .build());

    when(statsRepository.getPopularKeywords(
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
        .thenReturn(keywordsWithRankChanges);

    // when
    PopularKeywordResponse response = popularKeywordQueryService.getPopularKeywords(from, to, 10);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getKeywords()).hasSize(3);

    // 노트북: 2위 -> 1위 (상승)
    PopularKeywordResponse.KeywordStats notebook = response.getKeywords().get(0);
    assertThat(notebook.getKeyword()).isEqualTo("노트북");
    assertThat(notebook.getRank()).isEqualTo(1);
    assertThat(notebook.getPreviousRank()).isEqualTo(2);
    assertThat(notebook.getRankChange()).isEqualTo(1);
    assertThat(notebook.getChangeStatus()).isEqualTo(PopularKeywordResponse.RankChangeStatus.UP);
    assertThat(notebook.getClickThroughRate()).isEqualTo(30.0);

    // 키보드: 1위 -> 2위 (하락)
    PopularKeywordResponse.KeywordStats keyboard = response.getKeywords().get(1);
    assertThat(keyboard.getKeyword()).isEqualTo("키보드");
    assertThat(keyboard.getRank()).isEqualTo(2);
    assertThat(keyboard.getPreviousRank()).isEqualTo(1);
    assertThat(keyboard.getRankChange()).isEqualTo(-1);
    assertThat(keyboard.getChangeStatus()).isEqualTo(PopularKeywordResponse.RankChangeStatus.DOWN);

    // 마우스: 신규 진입
    PopularKeywordResponse.KeywordStats mouse = response.getKeywords().get(2);
    assertThat(mouse.getKeyword()).isEqualTo("마우스");
    assertThat(mouse.getRank()).isEqualTo(3);
    assertThat(mouse.getPreviousRank()).isNull();
    assertThat(mouse.getChangeStatus()).isEqualTo(PopularKeywordResponse.RankChangeStatus.NEW);
  }

  @Test
  @DisplayName("급등 검색어 조회")
  void getTrendingKeywords() {
    // given
    List<KeywordStats> currentKeywords =
        Arrays.asList(
            KeywordStats.builder()
                .keyword("아이폰15")
                .searchCount(200L)
                .clickCount(100L)
                .clickThroughRate(50.0)
                .percentage(40.0)
                .rank(1)
                .build(),
            KeywordStats.builder()
                .keyword("갤럭시S24")
                .searchCount(150L)
                .clickCount(60L)
                .clickThroughRate(40.0)
                .percentage(30.0)
                .rank(2)
                .build(),
            KeywordStats.builder()
                .keyword("에어팟")
                .searchCount(100L)
                .clickCount(30L)
                .clickThroughRate(30.0)
                .percentage(20.0)
                .rank(3)
                .build(),
            KeywordStats.builder()
                .keyword("애플워치")
                .searchCount(50L)
                .clickCount(10L)
                .clickThroughRate(20.0)
                .percentage(10.0)
                .rank(4)
                .build());

    List<KeywordStats> previousKeywords =
        Arrays.asList(
            KeywordStats.builder()
                .keyword("에어팟")
                .searchCount(80L)
                .clickCount(20L)
                .clickThroughRate(25.0)
                .percentage(40.0)
                .rank(1)
                .build(),
            KeywordStats.builder()
                .keyword("애플워치")
                .searchCount(60L)
                .clickCount(12L)
                .clickThroughRate(20.0)
                .percentage(30.0)
                .rank(2)
                .build());

    when(statsRepository.getPopularKeywords(
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
        .thenReturn(currentKeywords)
        .thenReturn(previousKeywords);

    // when
    PopularKeywordResponse response = popularKeywordQueryService.getTrendingKeywords(from, to, 2);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getKeywords()).hasSize(2);

    // 아이폰15와 갤럭시S24가 신규 진입으로 가장 큰 순위 상승
    assertThat(response.getKeywords().get(0).getKeyword()).isIn("아이폰15", "갤럭시S24");
    assertThat(response.getKeywords().get(1).getKeyword()).isIn("아이폰15", "갤럭시S24");
  }

  @Test
  @DisplayName("인기 검색어 조회 - 데이터가 없는 경우")
  void getPopularKeywords_NoData() {
    // given
    when(statsRepository.getPopularKeywords(
            any(LocalDateTime.class), any(LocalDateTime.class), anyInt()))
        .thenReturn(Collections.emptyList());

    // when
    PopularKeywordResponse response = popularKeywordQueryService.getPopularKeywords(from, to, 10);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getKeywords()).isEmpty();
    assertThat(response.getPeriod()).contains(from.toLocalDate().toString());
  }
}
