package com.yjlee.search.stats.service.query;

import com.yjlee.search.stats.domain.KeywordStats;
import com.yjlee.search.stats.dto.PopularKeywordResponse;
import com.yjlee.search.stats.repository.StatsRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularKeywordQueryService {

  private final StatsRepository statsRepository;

  public PopularKeywordResponse getPopularKeywords(
      LocalDateTime from, LocalDateTime to, int limit) {
    log.info("인기검색어 조회 - 기간: {} ~ {}, 제한: {}", from, to, limit);

    // 현재 기간의 인기 검색어 조회
    List<KeywordStats> currentKeywords = statsRepository.getPopularKeywords(from, to, limit);

    // 이전 기간 계산 (동일한 기간만큼 이전)
    long periodDays = java.time.Duration.between(from, to).toDays();
    LocalDateTime previousFrom = from.minusDays(periodDays);
    LocalDateTime previousTo = from;

    // 이전 기간의 인기 검색어 조회
    List<KeywordStats> previousKeywords =
        statsRepository.getPopularKeywords(previousFrom, previousTo, limit * 2);

    // 순위 변동 계산
    List<PopularKeywordResponse.KeywordStats> keywordsWithRankChange =
        calculateRankChanges(currentKeywords, previousKeywords);

    String period = from.toLocalDate() + " ~ " + to.toLocalDate();

    return PopularKeywordResponse.builder().keywords(keywordsWithRankChange).period(period).build();
  }

  public PopularKeywordResponse getTrendingKeywords(
      LocalDateTime from, LocalDateTime to, int limit) {
    log.info("급등검색어 조회 - 기간: {} ~ {}, 제한: {}", from, to, limit);

    LocalDateTime previousFrom =
        from.minusDays(to.toLocalDate().toEpochDay() - from.toLocalDate().toEpochDay());
    LocalDateTime previousTo = from;

    List<KeywordStats> currentKeywords = statsRepository.getPopularKeywords(from, to, limit * 2);
    List<KeywordStats> previousKeywords =
        statsRepository.getPopularKeywords(previousFrom, previousTo, limit * 2);

    // 이전 기간의 순위를 Map으로 저장
    Map<String, Integer> previousRanks =
        previousKeywords.stream()
            .collect(Collectors.toMap(KeywordStats::getKeyword, KeywordStats::getRank));

    // 이전 기간 마지막 순위 계산 (새로운 키워드의 기준 순위)
    int lastPreviousRank = previousKeywords.isEmpty() ? limit * 2 + 1 : previousKeywords.size() + 1;

    List<PopularKeywordResponse.KeywordStats> trendingKeywords =
        currentKeywords.stream()
            .map(
                current -> {
                  int previousRank =
                      previousRanks.getOrDefault(current.getKeyword(), lastPreviousRank);
                  int rankChange = previousRank - current.getRank();
                  return new RankChangeKeyword(current, rankChange);
                })
            .filter(item -> item.rankChange > 0) // 순위가 상승한 키워드만
            .sorted((a, b) -> Integer.compare(b.rankChange, a.rankChange)) // 순위 변동량 큰 순
            .limit(limit)
            .map(item -> convertToResponse(item.keywordStats))
            .collect(Collectors.toList());

    String period = from.toLocalDate() + " ~ " + to.toLocalDate();

    return PopularKeywordResponse.builder().keywords(trendingKeywords).period(period).build();
  }

  private List<PopularKeywordResponse.KeywordStats> calculateRankChanges(
      List<KeywordStats> currentKeywords, List<KeywordStats> previousKeywords) {

    // 이전 기간 키워드 순위 맵 생성
    Map<String, Integer> previousRankMap =
        previousKeywords.stream()
            .collect(
                Collectors.toMap(
                    KeywordStats::getKeyword,
                    KeywordStats::getRank,
                    (existing, replacement) -> existing));

    return currentKeywords.stream()
        .map(
            current -> {
              Integer previousRank = previousRankMap.get(current.getKeyword());
              Integer rankChange = null;
              PopularKeywordResponse.RankChangeStatus changeStatus = null;

              if (previousRank == null) {
                // 신규 진입
                changeStatus = PopularKeywordResponse.RankChangeStatus.NEW;
              } else {
                // 순위 변동 계산 (이전 순위 - 현재 순위, 양수면 상승)
                rankChange = previousRank - current.getRank();

                if (rankChange > 0) {
                  changeStatus = PopularKeywordResponse.RankChangeStatus.UP;
                } else if (rankChange < 0) {
                  changeStatus = PopularKeywordResponse.RankChangeStatus.DOWN;
                } else {
                  changeStatus = PopularKeywordResponse.RankChangeStatus.SAME;
                }
              }

              return PopularKeywordResponse.KeywordStats.builder()
                  .keyword(current.getKeyword())
                  .count(current.getSearchCount())
                  .clickCount(current.getClickCount())
                  .clickThroughRate(current.getClickThroughRate())
                  .percentage(current.getPercentage())
                  .rank(current.getRank())
                  .previousRank(previousRank)
                  .rankChange(rankChange)
                  .changeStatus(changeStatus)
                  .build();
            })
        .collect(Collectors.toList());
  }

  private PopularKeywordResponse.KeywordStats convertToResponse(KeywordStats stats) {
    return PopularKeywordResponse.KeywordStats.builder()
        .keyword(stats.getKeyword())
        .count(stats.getSearchCount())
        .clickCount(stats.getClickCount())
        .clickThroughRate(stats.getClickThroughRate())
        .percentage(stats.getPercentage())
        .rank(stats.getRank())
        .build();
  }

  private static class RankChangeKeyword {
    private final KeywordStats keywordStats;
    private final int rankChange;

    public RankChangeKeyword(KeywordStats keywordStats, int rankChange) {
      this.keywordStats = keywordStats;
      this.rankChange = rankChange;
    }
  }
}
