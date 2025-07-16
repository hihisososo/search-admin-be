package com.yjlee.search.search.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SearchQueryListResponse {
  long id;
  String name;
  String description;
  String indexName;
  LocalDateTime updatedAt;
}
