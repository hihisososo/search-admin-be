package com.yjlee.search.searchlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "검색 로그 필터 옵션 응답")
public class SearchLogFilterOptionsResponse {

  @Schema(description = "사용 가능한 인덱스명 목록")
  private List<String> indexNames;

  @Schema(description = "최근 검색 키워드 목록 (자동완성용)")
  private List<String> recentKeywords;

  @Schema(description = "자주 사용되는 클라이언트 IP 목록")
  private List<String> topClientIps;

  @Schema(description = "날짜 범위")
  private DateRange dateRange;

  @Schema(description = "응답시간 통계")
  private ResponseTimeStats responseTimeStats;

  @Getter
  @Builder
  @Schema(description = "날짜 범위")
  public static class DateRange {

    @Schema(description = "최소 날짜", example = "2025-01-01")
    private LocalDate minDate;

    @Schema(description = "최대 날짜", example = "2025-01-23")
    private LocalDate maxDate;
  }

  @Getter
  @Builder
  @Schema(description = "응답시간 통계")
  public static class ResponseTimeStats {

    @Schema(description = "최소 응답시간", example = "5")
    private Long min;

    @Schema(description = "최대 응답시간", example = "2500")
    private Long max;

    @Schema(description = "평균 응답시간", example = "85")
    private Long avg;
  }
}
