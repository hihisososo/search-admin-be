package com.yjlee.search.search.analysis.dto;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "색인 분석 요청")
public class IndexAnalysisRequest {

  @NotBlank(message = "쿼리는 필수입니다.")
  @Schema(description = "분석할 쿼리", example = "삼성전자 노트북 1kg", required = true)
  String query;

  @NotNull(message = "환경은 필수입니다.")
  @Schema(
      description = "사전 환경 (CURRENT: 현재 설정, DEV: 개발, PROD: 운영)",
      example = "CURRENT",
      required = true)
  DictionaryEnvironmentType environment;
}
