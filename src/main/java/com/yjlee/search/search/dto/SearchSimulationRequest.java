package com.yjlee.search.search.dto;

import com.yjlee.search.deployment.model.IndexEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "검색 시뮬레이션 요청")
public class SearchSimulationRequest extends SearchExecuteRequest {

  @NotNull(message = "환경 타입은 필수입니다")
  @Schema(description = "검색할 환경 (DEV: 개발, PROD: 운영)", example = "DEV")
  private IndexEnvironment.EnvironmentType environmentType;

  @Schema(description = "Elasticsearch explain 결과 포함 여부", example = "false")
  private boolean explain = false;
}
