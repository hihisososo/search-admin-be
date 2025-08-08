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
public class SynonymRecommendationListResponse {

  private Integer totalCount;
  private List<SynonymRecommendationDetail> recommendations;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SynonymRecommendationDetail {
    private String synonymGroup; // 정규화된 콤마 구분자 문자열
    private String reason;
    private Integer recommendationCount;
  }
}


