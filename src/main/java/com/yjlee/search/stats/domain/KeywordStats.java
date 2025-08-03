package com.yjlee.search.stats.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KeywordStats {
  String keyword;
  long searchCount;
  long clickCount;
  double clickThroughRate;
  double percentage;
  int rank;
  Integer previousRank;
  Integer rankChange;
  RankChangeStatus changeStatus;

  public enum RankChangeStatus {
    UP,
    DOWN,
    SAME,
    NEW
  }
}