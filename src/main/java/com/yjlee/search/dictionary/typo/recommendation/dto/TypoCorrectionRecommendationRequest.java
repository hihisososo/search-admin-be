package com.yjlee.search.dictionary.typo.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypoCorrectionRecommendationRequest {
  @Builder.Default private Integer sampleSize = 1000;
  private Integer desiredRecommendationCount;
}
