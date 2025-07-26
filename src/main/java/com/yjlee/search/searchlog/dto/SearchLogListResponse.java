package com.yjlee.search.searchlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "검색 로그 리스트 응답")
public class SearchLogListResponse {

  @Schema(description = "검색 로그 목록")
  private List<SearchLogResponse> content;

  @Schema(description = "전체 요소 수", example = "1205")
  private Long totalElements;

  @Schema(description = "전체 페이지 수", example = "25")
  private Integer totalPages;

  @Schema(description = "현재 페이지", example = "1")
  private Integer currentPage;

  @Schema(description = "페이지 크기", example = "50")
  private Integer size;

  @Schema(description = "다음 페이지 존재 여부", example = "true")
  private Boolean hasNext;

  @Schema(description = "이전 페이지 존재 여부", example = "false")
  private Boolean hasPrevious;
}
