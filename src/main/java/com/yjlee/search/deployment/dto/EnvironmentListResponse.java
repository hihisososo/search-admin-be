package com.yjlee.search.deployment.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnvironmentListResponse {
  private List<EnvironmentInfoResponse> environments;
  private int totalCount;

  public static EnvironmentListResponse of(List<EnvironmentInfoResponse> environments) {
    return EnvironmentListResponse.builder()
        .environments(environments)
        .totalCount(environments.size())
        .build();
  }
}
