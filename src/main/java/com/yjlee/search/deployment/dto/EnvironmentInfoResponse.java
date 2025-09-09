package com.yjlee.search.deployment.dto;

import com.yjlee.search.deployment.model.IndexEnvironment;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnvironmentInfoResponse {
  private String environmentType;
  private String environmentDescription;
  private String indexName;
  private String autocompleteIndexName;
  private Long documentCount;
  private String indexStatus;
  private String indexStatusDescription;
  private LocalDateTime indexDate;
  private String version;

  public static EnvironmentInfoResponse from(IndexEnvironment environment) {
    return EnvironmentInfoResponse.builder()
        .environmentType(environment.getEnvironmentType().name())
        .environmentDescription(environment.getEnvironmentType().getDescription())
        .indexName(environment.getIndexName())
        .documentCount(environment.getDocumentCount())
        .indexStatus(environment.getIndexStatus().name())
        .indexStatusDescription(environment.getIndexStatus().getDescription())
        .indexDate(environment.getIndexDate())
        .version(environment.getVersion())
        .build();
  }
}
