package com.yjlee.search.clicklog.controller;

import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.clicklog.dto.ClickLogResponse;
import com.yjlee.search.clicklog.service.ClickLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
  public ResponseEntity<ClickLogResponse> logClick(@Valid @RequestBody ClickLogRequest request) {
    return ResponseEntity.ok(clickLogService.logClick(request));
  }
}
