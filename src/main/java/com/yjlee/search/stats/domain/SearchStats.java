package com.yjlee.search.stats.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SearchStats {
  long totalSearchCount;
  long totalDocumentCount;
  double searchFailureRate;
  long errorCount;
  double averageResponseTimeMs;
  double successRate;
  long clickCount;
  double clickThroughRate;
}