package com.yjlee.search.dictionary.unit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "단위 사전 수정 요청")
public class UnitDictionaryUpdateRequest {

  @Size(max = 1000, message = "키워드는 1000자를 초과할 수 없습니다")
  @Schema(description = "단위 매핑 (예: kg,킬로그램)", example = "kg,킬로그램")
  private String keyword;
}
