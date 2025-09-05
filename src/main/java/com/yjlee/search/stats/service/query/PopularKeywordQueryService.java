package com.yjlee.search.stats.service.query;

import static com.yjlee.search.stats.dto.PopularKeywordResponse.RankChangeStatus.*;

import com.yjlee.search.stats.domain.KeywordStats;
import com.yjlee.search.stats.dto.PopularKeywordResponse;
import com.yjlee.search.stats.repository.StatsRepository;
import java.time.LocalDateTime;
import java.util.List;
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

    // 단일 ES 쿼리로 현재/이전 주기 동시 조회 (순위 변동 계산 포함)
    List<KeywordStats> keywordStats = statsRepository.getPopularKeywords(from, to, limit);

    // KeywordStats를 PopularKeywordResponse.KeywordStats로 변환
    List<PopularKeywordResponse.KeywordStats> keywords =
        keywordStats.stream().map(this::convertToResponse).collect(Collectors.toList());

    String period = from.toLocalDate() + " ~ " + to.toLocalDate();

    return PopularKeywordResponse.builder().keywords(keywords).period(period).build();
  }

  public PopularKeywordResponse getTrendingKeywords(
      LocalDateTime from, LocalDateTime to, int limit) {
    log.info("급등검색어 조회 - 기간: {} ~ {}, 제한: {}", from, to, limit);

    // 모든 키워드 조회 (limit 없이)
    List<KeywordStats> allKeywords =
        statsRepository.getPopularKeywords(from, to, Integer.MAX_VALUE);

    // 이전 기간 마지막 순위 계산 (새로운 키워드의 기준 순위)
    int lastPreviousRank =
        allKeywords.isEmpty()
            ? 10001
            : allKeywords.stream()
                    .mapToInt(k -> k.getPreviousRank() != null ? k.getPreviousRank() : 0)
                    .max()
                    .orElse(100)
                + 1;

    List<PopularKeywordResponse.KeywordStats> trendingKeywords =
        allKeywords.stream()
            .map(
                keyword -> {
                  // 순위 변동 계산
                  int previousRank =
                      keyword.getPreviousRank() != null
                          ? keyword.getPreviousRank()
                          : lastPreviousRank;
                  int rankChange = previousRank - keyword.getRank();

                  // rankChange 정보를 포함한 객체로 변환
                  return PopularKeywordResponse.KeywordStats.builder()
                      .keyword(keyword.getKeyword())
                      .count(keyword.getSearchCount())
                      .clickCount(keyword.getClickCount())
                      .clickThroughRate(keyword.getClickThroughRate())
                      .percentage(keyword.getPercentage())
                      .rank(keyword.getRank())
                      .previousRank(keyword.getPreviousRank())
                      .rankChange(rankChange)
                      .changeStatus(
                          keyword.getChangeStatus() != null
                              ? convertChangeStatus(keyword.getChangeStatus())
                              : PopularKeywordResponse.RankChangeStatus.NEW)
                      .build();
                })
            .filter(
                item -> item.getRankChange() != null && item.getRankChange() > 0) // 순위가 상승한 키워드만
            .sorted(
                (a, b) ->
                    Integer.compare(
                        b.getRankChange() != null ? b.getRankChange() : 0,
                        a.getRankChange() != null ? a.getRankChange() : 0)) // 순위 변동량 큰 순
            .limit(limit)
            .collect(Collectors.toList());

    String period = from.toLocalDate() + " ~ " + to.toLocalDate();

    return PopularKeywordResponse.builder().keywords(trendingKeywords).period(period).build();
  }

  private PopularKeywordResponse.KeywordStats convertToResponse(KeywordStats stats) {
    return PopularKeywordResponse.KeywordStats.builder()
        .keyword(stats.getKeyword())
        .count(stats.getSearchCount())
        .clickCount(stats.getClickCount())
        .clickThroughRate(stats.getClickThroughRate())
        .percentage(stats.getPercentage())
        .rank(stats.getRank())
        .previousRank(stats.getPreviousRank())
        .rankChange(stats.getRankChange())
        .changeStatus(convertChangeStatus(stats.getChangeStatus()))
        .build();
  }

  private PopularKeywordResponse.RankChangeStatus convertChangeStatus(
      KeywordStats.RankChangeStatus status) {
    if (status == null) return null;
    switch (status) {
      case UP:
        return PopularKeywordResponse.RankChangeStatus.UP;
      case DOWN:
        return PopularKeywordResponse.RankChangeStatus.DOWN;
      case SAME:
        return PopularKeywordResponse.RankChangeStatus.SAME;
      case NEW:
        return PopularKeywordResponse.RankChangeStatus.NEW;
      default:
        return null;
    }
  }
}
