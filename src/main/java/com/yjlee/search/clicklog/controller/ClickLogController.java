package com.yjlee.search.clicklog.controller;

import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.clicklog.dto.ClickLogResponse;
import com.yjlee.search.clicklog.service.ClickLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Click Log", description = "클릭 로그 API")
@RestController
@RequestMapping("/api/v1/click-logs")
@RequiredArgsConstructor
public class ClickLogController {

  private final ClickLogService clickLogService;

  @Operation(summary = "클릭 로그 저장", description = "검색 결과 클릭 이벤트를 로깅합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "500", description = "서버 오류")
  })
  @PostMapping
  public ResponseEntity<ClickLogResponse> logClick(
      @Valid @RequestBody ClickLogRequest request,
      HttpServletRequest httpRequest) {
    
    log.debug("클릭 로그 요청 - 세션: {}, 키워드: {}, 상품: {}", 
        request.getSearchSessionId(), 
        request.getSearchKeyword(),
        request.getClickedProductId());
    
    ClickLogResponse response = clickLogService.logClick(request, httpRequest);
    return ResponseEntity.ok(response);
  }
}