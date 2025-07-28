package com.yjlee.search.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "평가 실행 요청")
public class EvaluationExecuteRequest {

  @NotBlank(message = "리포트 이름은 필수입니다")
  @Schema(description = "리포트 이름", example = "2024년 1월 검색 성능 평가", required = true)
  private String reportName;

  @Min(value = 1, message = "검색 결과 개수는 1 이상이어야 합니다")
  @Max(value = 300, message = "검색 결과 개수는 300 이하여야 합니다")
  @Schema(description = "검색 결과 개수 (최대 300개)", example = "50", defaultValue = "50")
  private Integer retrievalSize = 50;
}
