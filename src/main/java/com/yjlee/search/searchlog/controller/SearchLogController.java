package com.yjlee.search.searchlog.controller;

import com.yjlee.search.searchlog.dto.SearchLogListRequest;
import com.yjlee.search.searchlog.dto.SearchLogListResponse;
import com.yjlee.search.searchlog.dto.SearchLogResponse;
import com.yjlee.search.searchlog.service.SearchLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Search Log", description = "검색 로그 관리 API")
@RestController
@RequestMapping("/api/v1/search-logs")
@RequiredArgsConstructor
public class SearchLogController {

  private final SearchLogService searchLogService;

  @Operation(summary = "검색 로그 조회", description = "다양한 조건으로 검색 로그를 조회합니다.")
  @GetMapping
  public ResponseEntity<SearchLogListResponse> getSearchLogs(
      @ParameterObject SearchLogListRequest request) {
    return ResponseEntity.ok(searchLogService.getSearchLogs(request));
  }

  @Operation(summary = "검색 로그 상세 조회", description = "특정 검색 로그의 상세 정보를 조회합니다.")
  @GetMapping("/{logId}")
  public ResponseEntity<SearchLogResponse> getSearchLogDetail(
      @Parameter(description = "검색 로그 ID", required = true) @PathVariable String logId) {
    return ResponseEntity.ok(searchLogService.getSearchLogDetail(logId));
  }
}
