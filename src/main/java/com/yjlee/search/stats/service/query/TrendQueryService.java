package com.yjlee.search.stats.service.query;

import com.yjlee.search.stats.domain.TrendData;
import com.yjlee.search.stats.dto.TrendResponse;
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
public class TrendQueryService {

  private final StatsRepository statsRepository;

  public TrendResponse getTrends(LocalDateTime from, LocalDateTime to, String interval) {
    log.info("시계열 추이 조회 - 기간: {} ~ {}, 간격: {}", from, to, interval);
    
    List<TrendData> trends = statsRepository.getTrends(from, to, interval);
    String period = from.toLocalDate() + " ~ " + to.toLocalDate();
    
    List<TrendResponse.TrendData> trendDataList = trends.stream()
        .map(trend -> TrendResponse.TrendData.builder()
            .timestamp(trend.getTimestamp())
            .searchCount(trend.getSearchCount())
            .clickCount(trend.getClickCount())
            .clickThroughRate(trend.getClickThroughRate())
            .averageResponseTime(trend.getAverageResponseTime())
            .label(trend.getLabel())
            .build())
        .collect(Collectors.toList());
    
    return TrendResponse.builder()
        .searchVolumeData(trendDataList)
        .responseTimeData(trendDataList)
        .period(period)
        .interval(interval)
        .build();
  }
}