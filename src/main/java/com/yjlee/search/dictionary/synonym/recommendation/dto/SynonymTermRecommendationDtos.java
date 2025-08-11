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
    private Double temperature;
    // 목표로 하는 유의어 추천 엔트리 개수( baseTerm+synonymTerm 쌍 ). null이면 한 번만 수행
    private Integer desiredRecommendationCount;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ItemResponse {
    private String baseTerm;
    private List<SynonymItem> synonyms; // 저장된 항목만 반환

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
