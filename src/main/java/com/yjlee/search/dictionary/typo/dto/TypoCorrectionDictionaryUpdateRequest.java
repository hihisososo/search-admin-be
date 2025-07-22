package com.yjlee.search.dictionary.typo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "오타교정 사전 수정 요청")
public class TypoCorrectionDictionaryUpdateRequest {

  @NotBlank(message = "오타 단어는 필수입니다")
  @Size(max = 100, message = "오타 단어는 100자 이하여야 합니다")
  @Schema(description = "오타 단어", example = "삼송", required = true)
  private String keyword;

  @NotBlank(message = "교정어는 필수입니다")
  @Size(max = 100, message = "교정어는 100자 이하여야 합니다")
  @Schema(description = "교정어", example = "삼성", required = true)
  private String correctedWord;

  @Size(max = 500, message = "설명은 500자 이하여야 합니다")
  @Schema(description = "설명", example = "삼성 브랜드 오타")
  private String description;
}
