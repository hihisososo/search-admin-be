package com.yjlee.search.stats.controller;

import com.yjlee.search.stats.dto.PopularKeywordResponse;
import com.yjlee.search.stats.dto.StatsResponse;
import com.yjlee.search.stats.dto.TrendResponse;
import com.yjlee.search.stats.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Stats", description = "통계 API")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

  private final StatsService statsService;

  @Operation(summary = "기본 통계 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @GetMapping
  public ResponseEntity<StatsResponse> getStats(
      @Parameter(description = "시작일시 (기본값: 7일 전)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @Parameter(description = "종료일시 (기본값: 현재)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to) {

    if (from == null) {
      from = LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
    }
    if (to == null) {
      to = LocalDateTime.now(ZoneOffset.UTC);
    }

    log.info("대시보드 통계 조회 - 기간: {} ~ {}", from, to);

    StatsResponse stats = statsService.getStats(from, to);
    return ResponseEntity.ok(stats);
  }

  @Operation(summary = "인기검색어 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @GetMapping("/popular-keywords")
  public ResponseEntity<PopularKeywordResponse> getPopularKeywords(
      @Parameter(description = "시작일시 (기본값: 7일 전)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @Parameter(description = "종료일시 (기본값: 현재)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to,
      @Parameter(description = "조회할 키워드 수 (기본값: 10)") @RequestParam(defaultValue = "10")
          int limit) {

    if (from == null) {
      from = LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
    }
    if (to == null) {
      to = LocalDateTime.now(ZoneOffset.UTC);
    }

    log.info("인기검색어 조회 - 기간: {} ~ {}, 제한: {}", from, to, limit);

    PopularKeywordResponse popularKeywords = statsService.getPopularKeywords(from, to, limit);
    return ResponseEntity.ok(popularKeywords);
  }

  @Operation(summary = "시계열 추이 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @GetMapping("/trends")
  public ResponseEntity<TrendResponse> getTrends(
      @Parameter(description = "시작일시 (기본값: 7일 전)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @Parameter(description = "종료일시 (기본값: 현재)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to,
      @Parameter(description = "집계 간격 (hour/day, 기본값: hour)") @RequestParam(defaultValue = "hour")
          String interval) {

    if (from == null) {
      from = LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
    }
    if (to == null) {
      to = LocalDateTime.now(ZoneOffset.UTC);
    }

    log.info("시계열 추이 조회 - 기간: {} ~ {}, 간격: {}", from, to, interval);

    TrendResponse trends = statsService.getTrends(from, to, interval);
    return ResponseEntity.ok(trends);
  }

  @Operation(summary = "급등검색어 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @GetMapping("/trending-keywords")
  public ResponseEntity<PopularKeywordResponse> getTrendingKeywords(
      @Parameter(description = "시작일시 (기본값: 7일 전)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @Parameter(description = "종료일시 (기본값: 현재)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to,
      @Parameter(description = "조회할 키워드 수 (기본값: 10)") @RequestParam(defaultValue = "10")
          int limit) {

    if (from == null) {
      from = LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
    }
    if (to == null) {
      to = LocalDateTime.now(ZoneOffset.UTC);
    }

    log.info("급등검색어 조회 - 기간: {} ~ {}, 제한: {}", from, to, limit);

    PopularKeywordResponse trendingKeywords = statsService.getTrendingKeywords(from, to, limit);
    return ResponseEntity.ok(trendingKeywords);
  }
}
