package com.yjlee.search.searchlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "급등 검색어 응답")
public class TrendingKeywordsResponse {

  @Schema(description = "급등 검색어 목록")
  private List<TrendingKeywordDto> keywords;

  @Schema(description = "현재 기간 시작", example = "2024-01-01T00:00:00")
  private LocalDateTime currentFromDate;

  @Schema(description = "현재 기간 종료", example = "2024-01-07T23:59:59")
  private LocalDateTime currentToDate;

  @Schema(description = "비교 기간 시작", example = "2023-12-25T00:00:00")
  private LocalDateTime previousFromDate;

  @Schema(description = "비교 기간 종료", example = "2023-12-31T23:59:59")
  private LocalDateTime previousToDate;

  @Schema(description = "총 급등 검색어 수", example = "10")
  private Integer totalCount;

  @Schema(description = "데이터 업데이트 시간", example = "2024-01-08T10:30:00")
  private LocalDateTime lastUpdated;
}
