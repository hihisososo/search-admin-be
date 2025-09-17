package com.yjlee.search.deployment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "색인 시작 응답")
public class IndexingStartResponse {

  @Schema(description = "작업 ID")
  private Long taskId;

  @Schema(description = "결과 메시지")
  private String message;

  public static IndexingStartResponse of(Long taskId, String message) {
    return IndexingStartResponse.builder().taskId(taskId).message(message).build();
  }
}
