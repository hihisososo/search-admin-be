package com.yjlee.search.dictionary.unit.dto;

import com.yjlee.search.dictionary.common.dto.BaseDictionaryCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "단위 사전 생성 요청")
public class UnitDictionaryCreateRequest implements BaseDictionaryCreateRequest {

  @NotBlank(message = "키워드는 필수입니다")
  @Size(max = 1000, message = "키워드는 1000자를 초과할 수 없습니다")
  @Schema(description = "단위 매핑 (예: kg,킬로그램)", example = "kg,킬로그램", required = true)
  private String keyword;

  public String getDescription() {
    return null;
  }
}
