package com.yjlee.search.searchlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "검색 로그 조회 요청")
public class SearchLogListRequest {

  @Schema(description = "페이지 번호 (0부터 시작)", example = "0", defaultValue = "0")
  private Integer page = 0;

  @Schema(description = "페이지 크기 (1~100)", example = "10", defaultValue = "10")
  private Integer size = 10;

  @Schema(description = "조회 시작 날짜", example = "2025-01-20T00:00:00Z")
  private LocalDateTime startDate;

  @Schema(description = "조회 종료 날짜", example = "2025-01-23T23:59:59Z")
  private LocalDateTime endDate;

  @Schema(description = "검색 키워드 필터", example = "삼성")
  private String keyword;

  @Schema(description = "인덱스명 필터", example = "products")
  private String indexName;

  @Schema(description = "에러 여부 필터", example = "false")
  private Boolean isError;

  @Schema(description = "클라이언트 IP 필터", example = "192.168.1.100")
  private String clientIp;

  @Schema(description = "최소 응답시간", example = "10")
  private Long minResponseTime;

  @Schema(description = "최대 응답시간", example = "1000")
  private Long maxResponseTime;

  @Schema(description = "최소 결과수", example = "0")
  private Long minResultCount;

  @Schema(description = "최대 결과수", example = "1000")
  private Long maxResultCount;

  @Schema(
      description = "정렬 필드",
      example = "timestamp",
      allowableValues = {"timestamp", "responseTime", "resultCount", "searchKeyword"})
  private String sort = "timestamp";

  @Schema(
      description = "정렬 순서",
      example = "desc",
      allowableValues = {"asc", "desc"})
  private String order = "desc";
}
