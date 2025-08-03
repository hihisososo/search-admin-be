package com.yjlee.search.stats.domain;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TrendData {
  LocalDateTime timestamp;
  long searchCount;
  long clickCount;
  double clickThroughRate;
  double averageResponseTime;
  String label;
}