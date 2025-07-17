package com.yjlee.search.searchsimulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SearchSimulationQueryCreateRequest {
  @NotBlank(message = "검색식 이름은 필수입니다.")
  @Size(max = 200, message = "검색식 이름은 200자를 초과할 수 없습니다.")
  String name;

  @Size(max = 500, message = "설명은 500자를 초과할 수 없습니다.")
  String description;

  @NotBlank(message = "Query DSL은 필수입니다.")
  String queryDsl;

  @NotBlank(message = "인덱스명은 필수입니다.")
  @Size(max = 100, message = "인덱스명은 100자를 초과할 수 없습니다.")
  String indexName;
}
