package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "상품 검색 요청")
public class SearchExecuteRequest {

  @Schema(description = "검색어", example = "아이폰", required = false)
  private String query;

  @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다")
  @Schema(description = "페이지 번호 (0부터 시작)", example = "0", defaultValue = "0")
  private Integer page = 0;

  @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
  @Schema(description = "페이지 크기", example = "10", defaultValue = "10")
  private Integer size = 10;

  @Valid
  @Schema(description = "정렬 옵션")
  private ProductSortDto sort;

  @Valid
  @Schema(description = "필터 옵션")
  private ProductFiltersDto filters;

  @Schema(description = "오타교정 적용 여부", example = "true", defaultValue = "true")
  private Boolean applyTypoCorrection = true;

  @Schema(description = "검색 세션 ID (FE에서 생성하여 전달)", example = "session-123456")
  private String searchSessionId;

  @Schema(description = "검색 모드", example = "KEYWORD_ONLY", defaultValue = "KEYWORD_ONLY")
  private SearchMode searchMode = SearchMode.KEYWORD_ONLY;

  @Schema(description = "RRF 알고리즘 K 상수 (rank + k)", example = "60", defaultValue = "60")
  private Integer rrfK = 60;

  @Schema(description = "하이브리드 검색 시 각 검색의 상위 K개 결과", example = "300", defaultValue = "300")
  private Integer hybridTopK = 300;
}
