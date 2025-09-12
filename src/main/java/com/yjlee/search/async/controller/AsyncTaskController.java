package com.yjlee.search.async.controller;

import com.yjlee.search.async.dto.AsyncTaskListResponse;
import com.yjlee.search.async.dto.AsyncTaskResponse;
import com.yjlee.search.async.service.AsyncTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "AsyncTask", description = "비동기 작업 관리 API")
public class AsyncTaskController {

  private final AsyncTaskService asyncTaskService;

  @GetMapping("/{taskId}")
  @Operation(summary = "비동기 작업 상태 조회", description = "특정 비동기 작업의 상태를 조회합니다")
  public ResponseEntity<AsyncTaskResponse> getTaskStatus(@PathVariable Long taskId) {
    return ResponseEntity.ok(asyncTaskService.getTaskOrThrow(taskId));
  }

  @GetMapping
  @Operation(summary = "비동기 작업 리스트 조회", description = "최근 7일간의 비동기 작업 목록을 조회합니다")
  public ResponseEntity<AsyncTaskListResponse> getTasks(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(asyncTaskService.getRecentTasks(page, size));
  }

  @GetMapping("/running")
  @Operation(summary = "실행 중인 작업 조회", description = "현재 실행 중인 모든 비동기 작업을 조회합니다")
  public ResponseEntity<List<AsyncTaskResponse>> getRunningTasks() {
    return ResponseEntity.ok(asyncTaskService.getRunningTasks());
  }
}
