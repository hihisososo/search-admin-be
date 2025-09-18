package com.yjlee.search.dictionary.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@Schema(description = "사용자 사전 생성 요청")
public class UserDictionaryCreateRequest {

  @NotBlank(message = "키워드는 필수입니다.")
  @Size(max = 1000, message = "키워드는 1000자를 초과할 수 없습니다.")
  @Schema(description = "사용자 정의 단어", example = "카카오페이", required = true)
  String keyword;
}
