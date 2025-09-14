package com.yjlee.search.deployment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "배포 요청")
public class DeploymentRequest {

  @Schema(description = "배포 설명")
  private String description;
}
