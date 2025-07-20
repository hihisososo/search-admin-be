package com.yjlee.search.stats.dto;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class StatsResponse {

  long totalSearchCount;
  long totalDocumentCount;
  double searchFailureRate;
  long errorCount;
  double averageResponseTimeMs;
  double successRate;
  String period;
}
