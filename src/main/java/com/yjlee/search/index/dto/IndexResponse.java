package com.yjlee.search.index.dto;

import java.time.ZonedDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class IndexResponse {
  long id;
  String name;
  String description;
  String status;
  long docCount;
  long size;
  ZonedDateTime lastIndexedAt;
  String fileName;
  Map<String, Object> mappings;
  Map<String, Object> settings;
}
