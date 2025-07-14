package com.yjlee.search.index.model;

import java.time.ZonedDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

@Getter
@ToString
@Builder
@Jacksonized
@With
public class Index {
  private final String id;
  private final String name;
  private final String status;
  private final int docCount;
  private final long size;
  private final ZonedDateTime lastIndexedAt;
  private final String dataSource; // db 또는 json
  private final String jdbcUrl;
  private final String jdbcUser;
  private final String jdbcPassword;
  private final String jdbcQuery;
  private final Map<String, Object> mapping;
  private final Map<String, Object> settings;
}
