package com.yjlee.search.loggen.controller;

import com.yjlee.search.loggen.dto.BulkLogGenerationRequest;
import com.yjlee.search.loggen.service.AutoLogGeneratorService;
import com.yjlee.search.loggen.service.BulkLogGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Log Generation", description = "로그 생성 관리 API")
@RestController
@RequestMapping("/api/v1/admin/logs")
@RequiredArgsConstructor
public class LogGenerationController {

  private final AutoLogGeneratorService autoLogGeneratorService;
  private final BulkLogGeneratorService bulkLogGeneratorService;

  @Operation(summary = "자동 로그 생성 시작", description = "자동으로 검색 및 클릭 로그를 생성합니다.")
  @PostMapping("/generate/start")
  public ResponseEntity<Map<String, String>> startAutoGeneration() {
    autoLogGeneratorService.startGeneration();
    return ResponseEntity.ok(Map.of("status", "started", "message", "자동 로그 생성이 시작되었습니다."));
  }

  @Operation(summary = "자동 로그 생성 중지", description = "자동 로그 생성을 중지합니다.")
  @PostMapping("/generate/stop")
  public ResponseEntity<Map<String, String>> stopAutoGeneration() {
    autoLogGeneratorService.stopGeneration();
    return ResponseEntity.ok(Map.of("status", "stopped", "message", "자동 로그 생성이 중지되었습니다."));
  }

  @Operation(summary = "대량 로그 생성", description = "지정된 기간 동안의 검색 및 클릭 로그를 대량으로 생성합니다.")
  @PostMapping("/generate-bulk")
  public ResponseEntity<Map<String, Object>> generateBulkLogs(
      @Valid @RequestBody BulkLogGenerationRequest request) {
    log.info(
        "대량 로그 생성 요청 - 기간: {} ~ {}, 일별: {}개, 클릭률: {}%",
        request.getStartDate(),
        request.getEndDate(),
        request.getLogsPerDay(),
        request.getClickRate() * 100);

    try {
      bulkLogGeneratorService.generateBulkLogs(request);

      return ResponseEntity.ok(
          Map.of(
              "status", "success",
              "message", "대량 로그 생성이 완료되었습니다.",
              "startDate", request.getStartDate(),
              "endDate", request.getEndDate(),
              "logsPerDay", request.getLogsPerDay(),
              "clickRate", request.getClickRate()));

    } catch (Exception e) {
      log.error("대량 로그 생성 실패", e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "error", "message", "대량 로그 생성 중 오류가 발생했습니다: " + e.getMessage()));
    }
  }

  @Operation(summary = "자동 로그 생성 상태 조회", description = "자동 로그 생성 상태를 조회합니다.")
  @GetMapping("/generate/status")
  public ResponseEntity<Map<String, Object>> getGenerationStatus() {
    boolean isRunning = autoLogGeneratorService.isRunning();
    return ResponseEntity.ok(
        Map.of("isRunning", isRunning, "status", isRunning ? "running" : "stopped"));
  }
}
