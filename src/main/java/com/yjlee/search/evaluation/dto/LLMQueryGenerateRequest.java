package com.yjlee.search.evaluation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LLMQueryGenerateRequest {
  @NotNull(message = "생성할 쿼리 개수는 필수입니다")
  @Min(1)
  @Max(100)
  private Integer count;

  @Builder.Default private Integer minCandidates = 60;
  @Builder.Default private Integer maxCandidates = 200;
  private String category;
}
