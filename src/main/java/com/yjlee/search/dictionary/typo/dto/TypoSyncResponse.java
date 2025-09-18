package com.yjlee.search.dictionary.typo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "오타교정 사전 동기화 응답")
public class TypoSyncResponse {

  @Schema(description = "동기화 성공 여부", example = "true")
  private boolean success;

  @Schema(description = "동기화 결과 메시지", example = "오타교정 사전 실시간 반영 완료")
  private String message;

  @Schema(description = "환경 타입", example = "CURRENT")
  private String environment;

  @Schema(description = "동기화 시간 (timestamp)", example = "1703123456789")
  private long timestamp;

  public static TypoSyncResponse success(String environment) {
    return TypoSyncResponse.builder()
        .success(true)
        .message("오타교정 사전 실시간 반영 완료")
        .environment(environment)
        .timestamp(System.currentTimeMillis())
        .build();
  }
}
