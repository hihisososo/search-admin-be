package com.yjlee.search.dictionary.synonym.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SynonymRecommendationRequest {

  @Builder.Default private Integer sampleSize = 100;
}


