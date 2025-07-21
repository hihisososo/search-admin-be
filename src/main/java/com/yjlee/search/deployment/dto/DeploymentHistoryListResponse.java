package com.yjlee.search.deployment.dto;

import com.yjlee.search.common.PageResponse;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

@Data
@Builder
public class DeploymentHistoryListResponse {
  private List<DeploymentHistoryResponse> deploymentHistories;
  private PageResponse<DeploymentHistoryResponse> pagination;

  public static DeploymentHistoryListResponse of(Page<DeploymentHistoryResponse> page) {
    return DeploymentHistoryListResponse.builder()
        .deploymentHistories(page.getContent())
        .pagination(PageResponse.from(page))
        .build();
  }
}
