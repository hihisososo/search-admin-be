package com.yjlee.search.dictionary.synonym.recommendation.dto;

import java.util.List;
import lombok.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SynonymTermRecommendationDtos {

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GenerateRequest {
    @Builder.Default private Integer sampleSize = 1000;
    private Integer desiredRecommendationCount;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ItemResponse {
    private String baseTerm;
    private List<SynonymItem> synonyms;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SynonymItem {
      private String term;
      private String reason;
      private int recommendationCount;
    }
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ListResponse {
    private int totalCount;
    private List<ItemResponse> items;
  }
}
