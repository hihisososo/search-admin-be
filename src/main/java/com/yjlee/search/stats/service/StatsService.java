package com.yjlee.search.stats.service;

import com.yjlee.search.stats.dto.PopularKeywordResponse;
import com.yjlee.search.stats.dto.StatsResponse;
import com.yjlee.search.stats.dto.TrendResponse;
import com.yjlee.search.stats.service.query.PopularKeywordQueryService;
import com.yjlee.search.stats.service.query.StatsQueryService;
import com.yjlee.search.stats.service.query.TrendQueryService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

  private final StatsQueryService statsQueryService;
  private final PopularKeywordQueryService popularKeywordQueryService;
  private final TrendQueryService trendQueryService;

  public StatsResponse getStats(LocalDateTime from, LocalDateTime to) {
    LocalDateTime actualFrom = from != null ? from : LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
    LocalDateTime actualTo = to != null ? to : LocalDateTime.now(ZoneOffset.UTC);
    log.info("대시보드 통계 조회 - 기간: {} ~ {}", actualFrom, actualTo);
    return statsQueryService.getStats(actualFrom, actualTo);
  }

  public PopularKeywordResponse getPopularKeywords(
      LocalDateTime from, LocalDateTime to, int limit) {
    LocalDateTime actualFrom = from != null ? from : LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
    LocalDateTime actualTo = to != null ? to : LocalDateTime.now(ZoneOffset.UTC);
    log.info("인기검색어 조회 - 기간: {} ~ {}, 제한: {}", actualFrom, actualTo, limit);
    return popularKeywordQueryService.getPopularKeywords(actualFrom, actualTo, limit);
  }

  public TrendResponse getTrends(LocalDateTime from, LocalDateTime to) {
    LocalDateTime actualFrom = from != null ? from : LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
    LocalDateTime actualTo = to != null ? to : LocalDateTime.now(ZoneOffset.UTC);
    log.info("시계열 추이 조회 - 기간: {} ~ {}", actualFrom, actualTo);
    return trendQueryService.getTrends(actualFrom, actualTo);
  }

  public PopularKeywordResponse getTrendingKeywords(
      LocalDateTime from, LocalDateTime to, int limit) {
    LocalDateTime actualFrom = from != null ? from : LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
    LocalDateTime actualTo = to != null ? to : LocalDateTime.now(ZoneOffset.UTC);
    log.info("급등검색어 조회 - 기간: {} ~ {}, 제한: {}", actualFrom, actualTo, limit);
    return popularKeywordQueryService.getTrendingKeywords(actualFrom, actualTo, limit);
  }
}
