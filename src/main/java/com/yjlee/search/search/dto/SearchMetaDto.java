package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "검색 메타 정보")
public class SearchMetaDto {

  @Schema(description = "현재 페이지", example = "1")
  private Integer page;

  @Schema(description = "페이지 크기", example = "10")
  private Integer size;

  @Schema(description = "전체 페이지 수", example = "16")
  private Integer totalPages;

  @Schema(description = "처리 시간(ms)", example = "23")
  private Long processingTime;
}
