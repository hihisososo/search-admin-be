package com.yjlee.search.async.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "비동기 작업 시작 응답")
public class AsyncTaskStartResponse {

  @Schema(description = "생성된 작업 ID", example = "123")
  private Long taskId;

  @Schema(description = "작업 시작 메시지", example = "비동기 작업이 시작되었습니다.")
  private String message;
}
