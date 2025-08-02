package com.yjlee.search.search.dto;

import com.yjlee.search.deployment.model.IndexEnvironment;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchSimulationParams extends SearchParams {

  @Parameter(description = "환경 타입 (DEV: 개발, PROD: 운영)", required = true)
  private IndexEnvironment.EnvironmentType environmentType;

  @Parameter(description = "Elasticsearch explain 결과 포함 여부")
  private boolean explain = false;
}
