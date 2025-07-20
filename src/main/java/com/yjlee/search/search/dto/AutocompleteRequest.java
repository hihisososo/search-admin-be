package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "자동완성 요청")
public class AutocompleteRequest {

  @NotBlank(message = "검색 키워드는 필수입니다")
  @Size(min = 1, max = 100, message = "검색 키워드는 1자 이상 100자 이하여야 합니다")
  @Schema(description = "검색 키워드", example = "검색어", required = true)
  private String keyword;
}
