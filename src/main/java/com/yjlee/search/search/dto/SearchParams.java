package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchParams {

  @Parameter(description = "검색어", required = true)
  @NotBlank
  private String query;

  @Parameter(description = "페이지 번호", required = true)
  @Min(1)
  private Integer page;

  @Parameter(description = "페이지 크기", required = true)
  @Min(1)
  @Max(100)
  private Integer size;

  @Parameter(description = "정렬 필드")
  private String sortField = "score";

  @Parameter(description = "정렬 순서")
  private String sortOrder = "desc";

  @Parameter(description = "브랜드 필터")
  private List<String> brand;

  @Parameter(description = "카테고리 필터")
  private List<String> category;

  @Parameter(description = "최소 가격")
  private Long priceFrom;

  @Parameter(description = "최대 가격")
  private Long priceTo;
}
