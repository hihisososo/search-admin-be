package com.yjlee.search.dashboard.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class IndexDistributionResponse {

  List<IndexStats> indices;
  String period;
  long totalSearchCount;

  @Value
  @Builder
  @Jacksonized
  public static class IndexStats {
    long searchCount;
    double percentage;
    double averageResponseTime;
    long errorCount;
    double successRate;
  }
}
