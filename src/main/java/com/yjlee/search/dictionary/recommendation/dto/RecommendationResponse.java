package com.yjlee.search.dictionary.recommendation.dto;

import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {

  private Integer totalGenerated;
  private Integer totalSaved;
  private Integer duplicatesSkipped;
  private List<RecommendationItem> recommendations;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RecommendationItem {
    private String word;
    private String reason;
  }
}
