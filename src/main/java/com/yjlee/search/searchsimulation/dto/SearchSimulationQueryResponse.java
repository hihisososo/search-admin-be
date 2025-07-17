package com.yjlee.search.searchsimulation.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SearchSimulationQueryResponse {
  long id;
  String name;
  String description;
  String queryDsl;
  String indexName;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
}
