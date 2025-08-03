package com.yjlee.search.clicklog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "클릭 로그 응답")
public class ClickLogResponse {

  @Schema(description = "성공 여부", example = "true")
  private boolean success;

  @Schema(description = "메시지", example = "클릭 로그가 성공적으로 저장되었습니다.")
  private String message;

  @Schema(description = "타임스탬프", example = "2024-01-01T10:00:00")
  private String timestamp;
}
