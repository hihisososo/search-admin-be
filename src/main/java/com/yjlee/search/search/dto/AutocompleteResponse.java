package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "자동완성 응답")
public class AutocompleteResponse {

  @Schema(description = "자동완성 결과 목록", example = "[\"검색어1\", \"검색어2\", \"검색어3\"]")
  private List<String> suggestions;

  @Schema(description = "결과 개수", example = "3")
  private int count;
}
