package com.yjlee.search.searchsimulation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SearchSimulationExecuteRequest {
  @NotBlank(message = "인덱스명은 필수입니다.")
  String indexName;

  @NotBlank(message = "Query DSL은 필수입니다.")
  String queryDsl;
}
