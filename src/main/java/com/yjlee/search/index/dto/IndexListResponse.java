package com.yjlee.search.index.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class IndexListResponse {
  long id;
  String name;
  String description;
  String status;
  long docCount;
  long size;
  LocalDateTime updatedAt;
}
