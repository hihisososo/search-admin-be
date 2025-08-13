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
  private Long correctCount;
  private Long incorrectCount;
  private Long unspecifiedCount;

  public QueryStatsDto(
      String query,
      Long documentCount,
      Long correctCount,
      Long incorrectCount,
      Long unspecifiedCount) {
    this.query = query;
    this.documentCount = documentCount != null ? documentCount : 0L;
    this.correctCount = correctCount != null ? correctCount : 0L;
    this.incorrectCount = incorrectCount != null ? incorrectCount : 0L;
    this.unspecifiedCount = unspecifiedCount != null ? unspecifiedCount : 0L;
  }
}
