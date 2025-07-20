package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "상품 필터")
public class ProductFiltersDto {

  @Schema(description = "브랜드 목록", example = "[\"Apple\", \"Samsung\"]")
  private List<String> brand;

  @Schema(description = "카테고리 목록", example = "[\"스마트폰\"]")
  private List<String> category;

  @Valid
  @Schema(description = "가격 범위")
  private PriceRangeDto priceRange;
}
