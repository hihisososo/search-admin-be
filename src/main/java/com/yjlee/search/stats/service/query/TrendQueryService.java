package com.yjlee.search.stats.service.query;

import com.yjlee.search.stats.domain.TrendData;
import com.yjlee.search.stats.dto.TrendResponse;
import com.yjlee.search.stats.repository.StatsRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    List<LocalDateTime> allTimestamps = generateAllTimestamps(from, to, interval);

    Map<String, TrendResponse.TrendData> trendDataMap = new LinkedHashMap<>();
    for (LocalDateTime timestamp : allTimestamps) {
      String label = formatLabel(timestamp, interval);
      trendDataMap.put(
          label,
          TrendResponse.TrendData.builder()
              .timestamp(timestamp)
              .searchCount(0L)
              .clickCount(0L)
              .clickThroughRate(0.0)
              .averageResponseTime(0.0)
              .label(label)
              .build());
    }

    for (TrendData trend : trends) {
      String label = trend.getLabel();
      log.debug(
          "Trend data - label: {}, timestamp: {}, searchCount: {}, avgResponseTime: {}",
          label,
          trend.getTimestamp(),
          trend.getSearchCount(),
          trend.getAverageResponseTime());

      if (trendDataMap.containsKey(label)) {
        trendDataMap.put(
            label,
            TrendResponse.TrendData.builder()
                .timestamp(trend.getTimestamp())
                .searchCount(trend.getSearchCount())
                .clickCount(trend.getClickCount())
                .clickThroughRate(trend.getClickThroughRate())
                .averageResponseTime(trend.getAverageResponseTime())
                .label(label)
                .build());
      } else {
        log.warn(
            "Label not found in trendDataMap: {}, available keys: {}",
            label,
            trendDataMap.keySet());
      }
    }

    List<TrendResponse.TrendData> trendDataList = new ArrayList<>(trendDataMap.values());

    return TrendResponse.builder()
        .searchVolumeData(trendDataList)
        .responseTimeData(trendDataList)
        .period(period)
        .interval(interval)
        .build();
  }

  private List<LocalDateTime> generateAllTimestamps(
      LocalDateTime from, LocalDateTime to, String interval) {
    List<LocalDateTime> timestamps = new ArrayList<>();
    LocalDateTime current = from;

    if ("hour".equalsIgnoreCase(interval)) {
      current = current.truncatedTo(ChronoUnit.HOURS);
      while (!current.isAfter(to)) {
        timestamps.add(current);
        current = current.plusHours(1);
      }
    } else if ("day".equalsIgnoreCase(interval)) {
      current = current.truncatedTo(ChronoUnit.DAYS);
      while (!current.isAfter(to)) {
        timestamps.add(current);
        current = current.plusDays(1);
      }
    }

    return timestamps;
  }

  private String formatLabel(LocalDateTime timestamp, String interval) {
    if ("hour".equalsIgnoreCase(interval)) {
      return timestamp.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    } else if ("day".equalsIgnoreCase(interval)) {
      return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
    return timestamp.toString();
  }
}
