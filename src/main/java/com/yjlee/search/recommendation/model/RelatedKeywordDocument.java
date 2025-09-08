package com.yjlee.search.recommendation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatedKeywordDocument {

  private String keyword;

  @JsonProperty("normalized_keyword")
  private String normalizedKeyword;

  @JsonProperty("related_keywords")
  private List<RelatedKeyword> relatedKeywords;

  @JsonProperty("updated_at")
  private LocalDateTime updatedAt;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RelatedKeyword {
    private String keyword;
    private Double score;

    @JsonProperty("common_clicks")
    private Integer commonClicks;
  }
}
