package com.yjlee.search.dashboard.dto;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class DashboardStatsResponse {

  long totalSearchCount;
  long totalDocumentCount;
  double searchFailureRate;
  long errorCount;
  double averageResponseTimeMs;
  double successRate;
  String period;
}
