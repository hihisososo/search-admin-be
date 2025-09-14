package com.yjlee.search.deployment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "환경 목록 응답")
public class EnvironmentListResponse {

  @Schema(description = "환경 정보 목록")
  private List<EnvironmentInfoResponse> environments;

  @Schema(description = "전체 개수")
  private int totalCount;

  public static EnvironmentListResponse from(List<EnvironmentInfoResponse> environments) {
    return EnvironmentListResponse.builder()
        .environments(environments)
        .totalCount(environments.size())
        .build();
  }
}
