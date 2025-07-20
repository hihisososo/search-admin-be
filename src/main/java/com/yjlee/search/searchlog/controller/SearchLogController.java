package com.yjlee.search.searchlog.controller;

import com.yjlee.search.searchlog.service.SearchLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
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

  @Operation(
      summary = "샘플 검색 로그 생성",
      description = "테스트용 샘플 검색 로그 데이터를 생성합니다. 상품명을 기반으로 다양한 검색 키워드를 생성하여 현재 날짜로 로그를 저장합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @PostMapping("/sample")
  public ResponseEntity<Map<String, Object>> generateSampleLogs(
      @Parameter(description = "생성할 로그 개수", example = "100") @RequestParam(defaultValue = "100")
          int count) {
    try {
      log.info("샘플 검색 로그 생성 요청 - 개수: {}", count);
      int generated = searchLogService.generateSampleLogs(count);

      return ResponseEntity.ok(
          Map.of("message", "샘플 검색 로그 생성 완료", "generated", generated, "success", true));
    } catch (Exception e) {
      log.error("샘플 검색 로그 생성 실패", e);
      return ResponseEntity.internalServerError()
          .body(Map.of("message", "샘플 검색 로그 생성 실패: " + e.getMessage(), "success", false));
    }
  }
}
