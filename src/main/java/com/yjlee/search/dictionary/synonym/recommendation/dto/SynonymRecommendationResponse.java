package com.yjlee.search.dictionary.synonym.recommendation.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SynonymRecommendationResponse {

  private Integer totalGenerated;
  private Integer totalSaved;
  private Integer duplicatesSkipped;
  private List<SynonymRecommendationItem> recommendations;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SynonymRecommendationItem {
    private String synonymGroup; // 예: "랩탑,laptop,노트북"
    private String reason;
  }
}


