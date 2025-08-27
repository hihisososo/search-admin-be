package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchParams {

  @Schema(description = "검색어", example = "노트북", required = false)
  private String query;

  @Schema(description = "페이지 번호 (0부터 시작)", example = "0", defaultValue = "0")
  @Min(0)
  private Integer page = 0;

  @Schema(description = "페이지 크기", example = "20", defaultValue = "20")
  @Min(1)
  @Max(100)
  private Integer size = 20;

  @Schema(description = "정렬 필드", example = "score", defaultValue = "score")
  private String sortField = "score";

  @Schema(description = "정렬 순서", example = "desc", defaultValue = "desc")
  private String sortOrder = "desc";

  @Schema(description = "브랜드 필터")
  private List<String> brand;

  @Schema(description = "카테고리 필터")
  private List<String> category;

  @Schema(description = "최소 가격", example = "10000")
  private Long priceFrom;

  @Schema(description = "최대 가격", example = "100000")
  private Long priceTo;

  @Schema(description = "검색 세션 ID (FE에서 생성하여 전달)", example = "session-123456")
  private String searchSessionId;
}
