package com.yjlee.search.deployment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeploymentOperationResponse {
  private boolean success;
  private String message;
  private String version;
  private Long historyId;

  public static DeploymentOperationResponse success(
      String message, String version, Long historyId) {
    return DeploymentOperationResponse.builder()
        .success(true)
        .message(message)
        .version(version)
        .historyId(historyId)
        .build();
  }

  public static DeploymentOperationResponse failure(String message) {
    return DeploymentOperationResponse.builder().success(false).message(message).build();
  }
}
