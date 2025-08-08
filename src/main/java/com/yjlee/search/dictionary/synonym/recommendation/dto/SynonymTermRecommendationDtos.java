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
    @Builder.Default private Integer topKTerms = 200; // 빈도 상위 K개 term 추출
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


