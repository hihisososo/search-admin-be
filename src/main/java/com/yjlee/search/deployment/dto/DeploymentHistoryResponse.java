package com.yjlee.search.deployment.dto;

import com.yjlee.search.deployment.model.DeploymentHistory;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeploymentHistoryResponse {
  private Long id;
  private String deploymentType;
  private String deploymentTypeDescription;
  private String status;
  private String statusDescription;
  private String version;
  private Long documentCount;
  private String description;
  private LocalDateTime deploymentTime;
  private LocalDateTime createdAt;

  public static DeploymentHistoryResponse from(DeploymentHistory history) {
    return DeploymentHistoryResponse.builder()
        .id(history.getId())
        .deploymentType(history.getDeploymentType().name())
        .deploymentTypeDescription(history.getDeploymentType().getDescription())
        .status(history.getStatus().name())
        .statusDescription(history.getStatus().getDescription())
        .version(history.getVersion())
        .documentCount(history.getDocumentCount())
        .description(history.getDescription())
        .deploymentTime(history.getDeploymentTime())
        .createdAt(history.getCreatedAt())
        .build();
  }
}
