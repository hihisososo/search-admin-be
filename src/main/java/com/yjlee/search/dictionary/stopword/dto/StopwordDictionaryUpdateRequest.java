package com.yjlee.search.dictionary.stopword.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopwordDictionaryUpdateRequest {

  @Size(max = 1000, message = "키워드는 1000자 이하여야 합니다")
  private String keyword;

  @Size(max = 500, message = "설명은 500자 이하여야 합니다")
  private String description;
}
