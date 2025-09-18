package com.yjlee.search.dictionary.stopword.dto;

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
@Schema(description = "불용어 사전 생성 요청")
public class StopwordDictionaryCreateRequest {

  @NotBlank(message = "키워드는 필수입니다")
  @Size(max = 1000, message = "키워드는 1000자 이하여야 합니다")
  @Schema(description = "불용어", example = "그리고", required = true)
  private String keyword;
}