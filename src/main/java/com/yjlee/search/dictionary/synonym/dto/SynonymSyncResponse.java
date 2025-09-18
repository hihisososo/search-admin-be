package com.yjlee.search.dictionary.synonym.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "동의어 사전 동기화 응답")
public class SynonymSyncResponse {

  @Schema(description = "동기화 성공 여부", example = "true")
  private boolean success;

  @Schema(description = "동기화 결과 메시지", example = "동의어 사전 실시간 반영 완료")
  private String message;

  @Schema(description = "환경 타입", example = "CURRENT")
  private String environment;

  @Schema(description = "동기화 시간 (timestamp)", example = "1703123456789")
  private long timestamp;

  public static SynonymSyncResponse success(String environment) {
    return SynonymSyncResponse.builder()
        .success(true)
        .message("동의어 사전 실시간 반영 완료")
        .environment(environment)
        .timestamp(System.currentTimeMillis())
        .build();
  }

  public static SynonymSyncResponse error(String environment, String errorMessage) {
    return SynonymSyncResponse.builder()
        .success(false)
        .message("동의어 사전 실시간 반영 실패: " + errorMessage)
        .environment(environment)
        .timestamp(System.currentTimeMillis())
        .build();
  }
}
