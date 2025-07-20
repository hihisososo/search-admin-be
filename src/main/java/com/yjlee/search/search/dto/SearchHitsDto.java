package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "검색 결과")
public class SearchHitsDto {

  @Schema(description = "총 결과 수", example = "156")
  private Long total;

  @Schema(description = "상품 목록")
  private List<ProductDto> data;
}
