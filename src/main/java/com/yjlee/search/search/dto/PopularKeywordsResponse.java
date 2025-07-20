package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "인기 검색어 응답")
public class PopularKeywordsResponse {

  @Schema(description = "인기 검색어 목록")
  private List<PopularKeywordDto> keywords;

  @Schema(description = "조회 기간 시작", example = "2024-01-01T00:00:00")
  private LocalDateTime fromDate;

  @Schema(description = "조회 기간 종료", example = "2024-01-07T23:59:59")
  private LocalDateTime toDate;

  @Schema(description = "총 검색어 수", example = "10")
  private Integer totalCount;

  @Schema(description = "데이터 업데이트 시간", example = "2024-01-08T10:30:00")
  private LocalDateTime lastUpdated;
}
