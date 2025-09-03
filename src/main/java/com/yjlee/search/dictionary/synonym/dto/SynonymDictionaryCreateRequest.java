package com.yjlee.search.dictionary.synonym.dto;

import com.yjlee.search.dictionary.common.dto.BaseDictionaryCreateRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SynonymDictionaryCreateRequest implements BaseDictionaryCreateRequest {
  @NotBlank(message = "키워드는 필수입니다.")
  @Size(max = 1000, message = "키워드는 1000자를 초과할 수 없습니다.")
  String keyword;

  @Size(max = 500, message = "설명은 500자를 초과할 수 없습니다.")
  String description;
}
