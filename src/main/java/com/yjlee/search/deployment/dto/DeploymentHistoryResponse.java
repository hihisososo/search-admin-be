package com.yjlee.search.deployment.dto;

import com.yjlee.search.deployment.model.DeploymentHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "배포 이력 응답")
public class DeploymentHistoryResponse {

  @Schema(description = "이력 ID")
  private Long id;

  @Schema(description = "배포 타입")
  private String deploymentType;

  @Schema(description = "배포 타입 설명")
  private String deploymentTypeDescription;

  @Schema(description = "상태")
  private String status;

  @Schema(description = "상태 설명")
  private String statusDescription;

  @Schema(description = "버전")
  private String version;

  @Schema(description = "문서 개수")
  private Long documentCount;

  @Schema(description = "설명")
  private String description;

  @Schema(description = "배포 시간")
  private LocalDateTime deploymentTime;

  @Schema(description = "생성 시간")
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
