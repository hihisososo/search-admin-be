package com.yjlee.search.dictionary.unit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "단위 사전 동기화 응답")
public class UnitSyncResponse {

  @Schema(description = "동기화 성공 여부", example = "false")
  private boolean success;

  @Schema(description = "동기화 결과 메시지", example = "단위 사전은 실시간 반영이 불가능합니다. 인덱스를 재생성해야 적용됩니다.")
  private String message;

  @Schema(description = "환경 타입", example = "CURRENT")
  private String environment;

  public static UnitSyncResponse error(String environment) {
    return UnitSyncResponse.builder()
        .success(false)
        .message("단위 사전은 실시간 반영이 불가능합니다. 인덱스를 재생성해야 적용됩니다.")
        .environment(environment)
        .build();
  }
}