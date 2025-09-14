package com.yjlee.search.deployment.dto;

import com.yjlee.search.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

@Data
@Builder
@Schema(description = "배포 이력 목록 응답")
public class DeploymentHistoryListResponse {

  @Schema(description = "배포 이력 목록")
  private List<DeploymentHistoryResponse> deploymentHistories;

  @Schema(description = "페이지 정보")
  private PageResponse<DeploymentHistoryResponse> pagination;

  public static DeploymentHistoryListResponse from(Page<DeploymentHistoryResponse> page) {
    return DeploymentHistoryListResponse.builder()
        .deploymentHistories(page.getContent())
        .pagination(PageResponse.from(page))
        .build();
  }
}
