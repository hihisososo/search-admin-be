package com.yjlee.search.searchlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "급등 검색어")
public class TrendingKeywordDto {

  @Schema(description = "검색어", example = "갤럭시 S25")
  private String keyword;

  @Schema(description = "현재 검색 횟수", example = "850")
  private Long currentCount;

  @Schema(description = "이전 기간 검색 횟수", example = "120")
  private Long previousCount;

  @Schema(description = "증가율 (%)", example = "608.3")
  private Double growthRate;

  @Schema(description = "순위", example = "1")
  private Integer rank;
}
