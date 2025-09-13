package com.yjlee.search.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "임시 인덱스 갱신 응답")
public class TempIndexRefreshResponse {

  @Schema(description = "처리 상태", example = "success")
  String status;

  @Schema(description = "처리 결과 메시지", example = "임시 인덱스가 성공적으로 갱신되었습니다")
  String message;

  @Schema(description = "생성된 임시 인덱스 이름", example = "temp-analysis-current")
  String indexName;
}
