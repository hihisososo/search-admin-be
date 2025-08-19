package com.yjlee.search.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "비동기 평가 실행 요청")
public class EvaluationExecuteAsyncRequest {

  @NotBlank(message = "리포트명은 필수입니다")
  @Schema(description = "평가 리포트명", example = "2025-01 검색 품질 평가", required = true)
  private String reportName;

  @Min(value = 1, message = "검색 결과 개수는 1 이상이어야 합니다")
  @Schema(description = "검색 결과 개수", example = "100", defaultValue = "100")
  @Builder.Default
  private Integer retrievalSize = 100;
}