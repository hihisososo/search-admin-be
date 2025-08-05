package com.yjlee.search.searchlog.controller;

import com.yjlee.search.searchlog.dto.SearchLogListRequest;
import com.yjlee.search.searchlog.dto.SearchLogListResponse;
import com.yjlee.search.searchlog.service.SearchLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Search Log", description = "검색 로그 관리 API")
@RestController
@RequestMapping("/api/v1/search-logs")
@RequiredArgsConstructor
public class SearchLogController {

  private final SearchLogService searchLogService;

  @Operation(summary = "검색 로그 조회", description = "다양한 조건으로 검색 로그를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @GetMapping
  public ResponseEntity<SearchLogListResponse> getSearchLogs(
      @ModelAttribute SearchLogListRequest request) {
    try {
      log.info(
          "검색 로그 조회 요청 - 페이지: {}, 크기: {}, 키워드: {}",
          request.getPage(),
          request.getSize(),
          request.getKeyword());
      SearchLogListResponse response = searchLogService.getSearchLogs(request);
      log.info(
          "검색 로그 조회 완료 - 실제 페이지: {}/{}, 크기: {}, 조회된 건수: {}, 총 건수: {}",
          response.getCurrentPage(),
          response.getTotalPages(),
          response.getSize(),
          response.getContent().size(),
          response.getTotalElements());
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("검색 로그 조회 실패", e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
