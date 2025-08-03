package com.yjlee.search.search.dto;

import com.yjlee.search.deployment.model.IndexEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchSimulationParams extends SearchParams {

  @Schema(description = "환경 타입 (DEV: 개발, PROD: 운영)", example = "DEV", required = true)
  private IndexEnvironment.EnvironmentType environmentType;

  @Schema(description = "Elasticsearch explain 결과 포함 여부", example = "false", defaultValue = "false")
  private boolean explain = false;
}
