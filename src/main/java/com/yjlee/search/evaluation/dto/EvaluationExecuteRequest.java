package com.yjlee.search.evaluation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
}
