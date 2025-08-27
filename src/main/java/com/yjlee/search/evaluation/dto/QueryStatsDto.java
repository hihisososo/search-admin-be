package com.yjlee.search.evaluation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
public class QueryStatsDto {
  private String query;
  private Long documentCount;
  private Long score1Count; // 연관(1점) 개수
  private Long score0Count; // 비연관(0점) 개수
  private Long unevaluatedCount; // 미평가(null) 개수

  public QueryStatsDto(
      String query,
      Long documentCount,
      Long score1Count,
      Long score0Count,
      Long unevaluatedCount) {
    this.query = query;
    this.documentCount = documentCount != null ? documentCount : 0L;
    this.score1Count = score1Count != null ? score1Count : 0L;
    this.score0Count = score0Count != null ? score0Count : 0L;
    this.unevaluatedCount = unevaluatedCount != null ? unevaluatedCount : 0L;
  }
}
