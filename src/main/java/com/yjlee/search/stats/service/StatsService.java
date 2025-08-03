package com.yjlee.search.stats.service;

import com.yjlee.search.stats.dto.PopularKeywordResponse;
import com.yjlee.search.stats.dto.StatsResponse;
import com.yjlee.search.stats.dto.TrendResponse;
import com.yjlee.search.stats.service.query.PopularKeywordQueryService;
import com.yjlee.search.stats.service.query.StatsQueryService;
import com.yjlee.search.stats.service.query.TrendQueryService;
import java.time.LocalDateTime;
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
    return statsQueryService.getStats(from, to);
  }

  public PopularKeywordResponse getPopularKeywords(
      LocalDateTime from, LocalDateTime to, int limit) {
    return popularKeywordQueryService.getPopularKeywords(from, to, limit);
  }

  public TrendResponse getTrends(LocalDateTime from, LocalDateTime to, String interval) {
    return trendQueryService.getTrends(from, to, interval);
  }

  public PopularKeywordResponse getTrendingKeywords(
      LocalDateTime from, LocalDateTime to, int limit) {
    return popularKeywordQueryService.getTrendingKeywords(from, to, limit);
  }
}
