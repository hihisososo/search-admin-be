package com.yjlee.search.loggen.controller;

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

  @Operation(summary = "로그 생성기 활성화")
  @PostMapping("/enable")
  public ResponseEntity<String> enable() {
    System.setProperty("app.log-generator.enabled", "true");
    return ResponseEntity.ok("로그 생성기가 활성화되었습니다.");
  }

  @Operation(summary = "로그 생성기 비활성화")
  @PostMapping("/disable")
  public ResponseEntity<String> disable() {
    System.setProperty("app.log-generator.enabled", "false");
    return ResponseEntity.ok("로그 생성기가 비활성화되었습니다.");
  }
}
