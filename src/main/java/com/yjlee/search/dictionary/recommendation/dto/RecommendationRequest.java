package com.yjlee.search.dictionary.recommendation.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequest {

  @Builder.Default private Integer sampleSize = 100;
}
