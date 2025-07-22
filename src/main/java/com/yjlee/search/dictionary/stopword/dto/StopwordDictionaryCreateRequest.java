package com.yjlee.search.dictionary.stopword.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StopwordDictionaryCreateRequest {

  @NotBlank(message = "키워드는 필수입니다")
  @Size(max = 1000, message = "키워드는 1000자 이하여야 합니다")
  private String keyword;

  @Size(max = 500, message = "설명은 500자 이하여야 합니다")
  private String description;
}
