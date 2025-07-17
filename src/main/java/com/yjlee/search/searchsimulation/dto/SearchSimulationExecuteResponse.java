package com.yjlee.search.searchsimulation.dto;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SearchSimulationExecuteResponse {
  String indexName;
  Object searchResult; // ES 검색 결과를 그대로 반환
  long took; // 검색 소요 시간 (ms)
}
