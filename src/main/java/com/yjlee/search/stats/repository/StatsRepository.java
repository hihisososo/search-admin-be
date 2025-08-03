package com.yjlee.search.stats.repository;

import com.yjlee.search.stats.domain.KeywordStats;
import com.yjlee.search.stats.domain.SearchStats;
import com.yjlee.search.stats.domain.TrendData;
import java.time.LocalDateTime;
import java.util.List;

public interface StatsRepository {

  SearchStats getSearchStats(LocalDateTime from, LocalDateTime to);

  List<KeywordStats> getPopularKeywords(LocalDateTime from, LocalDateTime to, int limit);

  List<TrendData> getTrends(LocalDateTime from, LocalDateTime to, String interval);

  long getTotalSearchCount(LocalDateTime from, LocalDateTime to);

  long getClickCount(LocalDateTime from, LocalDateTime to);

  long getClickCountForKeyword(String keyword, LocalDateTime from, LocalDateTime to);
}
