package com.yjlee.search.dictionary.stopword.recommendation.dto;

import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopwordRecommendationListResponse {
  private Integer totalCount;
  private List<StopwordDetail> recommendations;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StopwordDetail {
    private String term;
    private String reason;
    private Integer recommendationCount;
  }
}


