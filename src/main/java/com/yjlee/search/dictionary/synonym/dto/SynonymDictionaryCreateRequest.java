package com.yjlee.search.dictionary.synonym.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@Schema(description = "동의어 사전 생성 요청")
public class SynonymDictionaryCreateRequest {

  @NotBlank(message = "키워드는 필수입니다.")
  @Size(max = 1000, message = "키워드는 1000자를 초과할 수 없습니다.")
  @Schema(description = "동의어 그룹 (콤마로 구분)", example = "TV,티브이,텔레비전", required = true)
  String keyword;
}