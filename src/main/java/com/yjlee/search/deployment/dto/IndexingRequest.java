package com.yjlee.search.deployment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "색인 요청")
public class IndexingRequest {

  @Schema(description = "색인 설명")
  private String description;
}
