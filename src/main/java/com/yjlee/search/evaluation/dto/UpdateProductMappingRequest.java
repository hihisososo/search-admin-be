package com.yjlee.search.evaluation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProductMappingRequest {

  @NotNull(message = "점수는 필수입니다")
  @Min(value = -1, message = "점수는 -1 이상이어야 합니다")
  @Max(value = 2, message = "점수는 2 이하여야 합니다")
  private Integer relevanceScore;

  private String evaluationReason;

  @Min(value = 0, message = "신뢰도는 0 이상이어야 합니다")
  @Max(value = 1, message = "신뢰도는 1 이하여야 합니다")
  private Double confidence;
}
