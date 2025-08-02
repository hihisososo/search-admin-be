package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "상품 검색 요청")
public class SearchExecuteRequest {

  @NotBlank(message = "검색어는 필수입니다")
  @Schema(description = "검색어", example = "아이폰", required = true)
  private String query;

  @NotNull(message = "페이지 번호는 필수입니다")
  @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다")
  @Schema(description = "페이지 번호", example = "1", required = true)
  private Integer page;

  @NotNull(message = "페이지 크기는 필수입니다")
  @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
  @Schema(description = "페이지 크기", example = "10", required = true)
  private Integer size;

  @Valid
  @Schema(description = "정렬 옵션")
  private ProductSortDto sort;

  @Valid
  @Schema(description = "필터 옵션")
  private ProductFiltersDto filters;

  @Schema(description = "오타교정 적용 여부", example = "true", defaultValue = "true")
  private Boolean applyTypoCorrection = true;
}
