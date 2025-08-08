package com.yjlee.search.dictionary.typo.recommendation.dto;

import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypoCorrectionRecommendationListResponse {
  private Integer totalCount;
  private List<TypoCorrectionDetail> recommendations;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TypoCorrectionDetail {
    private String pair; // "원문,교정" (공백 기준 교정 페어)
    private String reason;
    private Integer recommendationCount;
  }
}


