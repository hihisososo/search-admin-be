package com.yjlee.search.dictionary.recommendation.dto;

import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationListResponse {

  private Integer totalCount;
  private List<RecommendationDetail> recommendations;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RecommendationDetail {
    private String word;
    private String reason;
    private Integer recommendationCount;
  }
}
