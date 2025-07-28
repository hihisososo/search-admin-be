package com.yjlee.search.evaluation.dto;

import com.yjlee.search.evaluation.model.RelevanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProductMappingRequest {

  @NotNull(message = "연관성은 필수입니다")
  private RelevanceStatus relevanceStatus;

  private String evaluationReason;
}
