package com.yjlee.search.dictionary.stopword.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopwordRecommendationRequest {
  @Builder.Default private Integer sampleSize = 100;
}


