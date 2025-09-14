package com.yjlee.search.loggen.controller;

import com.yjlee.search.loggen.service.AutoLogGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Log Generation", description = "로그 생성 관리 API")
@RestController
@RequestMapping("/api/v1/admin/logs")
@RequiredArgsConstructor
public class LogGenerationController {

  private final AutoLogGeneratorService autoLogGeneratorService;

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

  @Operation(summary = "자동 로그 생성 상태 조회", description = "자동 로그 생성 상태를 조회합니다.")
  @GetMapping("/generate/status")
  public ResponseEntity<Map<String, Object>> getGenerationStatus() {
    boolean isRunning = autoLogGeneratorService.isRunning();
    return ResponseEntity.ok(
        Map.of("isRunning", isRunning, "status", isRunning ? "running" : "stopped"));
  }
}
