package com.yjlee.search.loggen.controller;

import com.yjlee.search.loggen.service.LogGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Log Generator", description = "로그 생성기 관리 API")
@RestController
@RequestMapping("/api/v1/log-generator")
@RequiredArgsConstructor
public class LogGeneratorController {
  
  private final LogGeneratorService logGeneratorService;

  @Operation(summary = "로그 생성기 활성화")
  @PostMapping("/enable")
  public ResponseEntity<String> enable() {
    logGeneratorService.enableLogGenerator();
    return ResponseEntity.ok(logGeneratorService.getEnabledMessage());
  }

  @Operation(summary = "로그 생성기 비활성화")
  @PostMapping("/disable")
  public ResponseEntity<String> disable() {
    logGeneratorService.disableLogGenerator();
    return ResponseEntity.ok(logGeneratorService.getDisabledMessage());
  }
}
