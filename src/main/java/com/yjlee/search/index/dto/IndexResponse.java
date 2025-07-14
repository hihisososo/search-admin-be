package com.yjlee.search.index.dto;

import java.time.ZonedDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

@Getter
@ToString
@Builder
@Jacksonized
public class IndexResponse {
  private final String id;
  private final String name;
  private final String status;
  private final int docCount;
  private final long size;
  private final ZonedDateTime lastIndexedAt;
  private final String dataSource;
  private final String jdbcUrl;
  private final String jdbcUser;
  private final String jdbcQuery;
  private final Map<String, Object> mapping;
  private final Map<String, Object> settings;
}
