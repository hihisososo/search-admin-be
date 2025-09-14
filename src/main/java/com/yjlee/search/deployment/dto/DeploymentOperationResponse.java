package com.yjlee.search.deployment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "배포 작업 응답")
public class DeploymentOperationResponse {

  @Schema(description = "결과 메시지")
  private String message;

  @Schema(description = "배포 버전")
  private String version;

  @Schema(description = "이력 ID")
  private Long historyId;
}
