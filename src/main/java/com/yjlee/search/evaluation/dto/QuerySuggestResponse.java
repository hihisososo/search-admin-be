package com.yjlee.search.evaluation.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuerySuggestResponse {
  private Integer requestedCount;
  private Integer returnedCount;
  private Integer minCandidates;
  private Integer maxCandidates;
  private List<SuggestItem> items;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class SuggestItem {
    private String query;
    private Integer candidateCount;
  }
}
