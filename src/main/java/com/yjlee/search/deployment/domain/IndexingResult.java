package com.yjlee.search.deployment.domain;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class IndexingResult {
  private String version;
  private int documentCount;
  private String indexName;
}
