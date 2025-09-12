package com.yjlee.search.stats.controller;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

import com.yjlee.search.stats.dto.PopularKeywordResponse;
import com.yjlee.search.stats.dto.StatsResponse;
import com.yjlee.search.stats.dto.TrendResponse;
import com.yjlee.search.stats.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Stats", description = "통계 API")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

  private final StatsService statsService;

  @Operation(summary = "기본 통계 조회")
  @GetMapping
  public ResponseEntity<StatsResponse> getStats(
      @Parameter(description = "시작일시 (기본값: 7일 전)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = ISO.DATE_TIME)
          LocalDateTime from,
      @Parameter(description = "종료일시 (기본값: 현재)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = ISO.DATE_TIME)
          LocalDateTime to) {
    return ResponseEntity.ok(statsService.getStats(from, to));
  }

  @Operation(summary = "인기검색어 조회")
  @GetMapping("/popular-keywords")
  public ResponseEntity<PopularKeywordResponse> getPopularKeywords(
      @Parameter(description = "시작일시 (기본값: 7일 전)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = ISO.DATE_TIME)
          LocalDateTime from,
      @Parameter(description = "종료일시 (기본값: 현재)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = ISO.DATE_TIME)
          LocalDateTime to,
      @Parameter(description = "조회할 키워드 수 (기본값: 10)") @RequestParam(defaultValue = "10")
          int limit) {
    return ResponseEntity.ok(statsService.getPopularKeywords(from, to, limit));
  }

  @Operation(summary = "시계열 추이 조회")
  @GetMapping("/trends")
  public ResponseEntity<TrendResponse> getTrends(
      @Parameter(description = "시작일시 (기본값: 7일 전)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = ISO.DATE_TIME)
          LocalDateTime from,
      @Parameter(description = "종료일시 (기본값: 현재)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = ISO.DATE_TIME)
          LocalDateTime to) {
    return ResponseEntity.ok(statsService.getTrends(from, to));
  }

  @Operation(summary = "급등검색어 조회")
  @GetMapping("/trending-keywords")
  public ResponseEntity<PopularKeywordResponse> getTrendingKeywords(
      @Parameter(description = "시작일시 (기본값: 7일 전)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = ISO.DATE_TIME)
          LocalDateTime from,
      @Parameter(description = "종료일시 (기본값: 현재)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = ISO.DATE_TIME)
          LocalDateTime to,
      @Parameter(description = "조회할 키워드 수 (기본값: 10)") @RequestParam(defaultValue = "10")
          int limit) {
    return ResponseEntity.ok(statsService.getTrendingKeywords(from, to, limit));
  }
}
