package com.yjlee.search.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class TrendResponse {

  List<TrendData> searchVolumeData;
  List<TrendData> responseTimeData;
  String period;
  String interval;

  @Value
  @Builder
  @Jacksonized
  public static class TrendData {
    LocalDateTime timestamp;
    long searchCount;
    double averageResponseTime;
    String label;
  }
}
