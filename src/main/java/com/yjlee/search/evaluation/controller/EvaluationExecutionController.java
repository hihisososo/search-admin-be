package com.yjlee.search.evaluation.controller;

import com.yjlee.search.evaluation.dto.AsyncTaskListResponse;
import com.yjlee.search.evaluation.dto.AsyncTaskResponse;
import com.yjlee.search.evaluation.dto.AsyncTaskStartResponse;
import com.yjlee.search.evaluation.dto.EvaluationExecuteAsyncRequest;
import com.yjlee.search.evaluation.dto.EvaluationExecuteRequest;
import com.yjlee.search.evaluation.dto.EvaluationExecuteResponse;
import com.yjlee.search.evaluation.dto.EvaluationReportDetailResponse;
import com.yjlee.search.evaluation.dto.EvaluationReportSummaryResponse;
import com.yjlee.search.evaluation.dto.LLMEvaluationRequest;
import com.yjlee.search.evaluation.model.EvaluationReport;
import com.yjlee.search.evaluation.service.AsyncEvaluationService;
import com.yjlee.search.evaluation.service.AsyncTaskService;
import com.yjlee.search.evaluation.service.EvaluationReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/evaluation")
@RequiredArgsConstructor
@Tag(name = "Evaluation Execution & History", description = "평가 실행 및 히스토리 API")
public class EvaluationExecutionController {

  private final EvaluationReportService evaluationReportService;
  private final AsyncEvaluationService asyncEvaluationService;
  private final AsyncTaskService asyncTaskService;

  @PostMapping("/candidates/evaluate-llm-async")
  @Operation(summary = "LLM 자동 후보군 평가 (비동기)")
  public ResponseEntity<AsyncTaskStartResponse> evaluateCandidatesWithLLMAsync(
      @RequestBody LLMEvaluationRequest request) {
    Long taskId = asyncEvaluationService.startLLMEvaluation(request);
    return ResponseEntity.ok(
        AsyncTaskStartResponse.builder()
            .taskId(taskId)
            .message("LLM 평가 작업이 시작되었습니다. 작업 ID: " + taskId)
            .build());
  }

  @GetMapping("/tasks/{taskId}")
  @Operation(summary = "비동기 작업 상태 조회")
  public ResponseEntity<AsyncTaskResponse> getTaskStatus(@PathVariable Long taskId) {
    return asyncTaskService
        .getTask(taskId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/tasks")
  @Operation(summary = "비동기 작업 리스트 조회")
  public ResponseEntity<AsyncTaskListResponse> getTasks(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    AsyncTaskListResponse response = asyncTaskService.getRecentTasks(page, size);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/tasks/running")
  @Operation(summary = "실행 중인 작업 조회")
  public ResponseEntity<List<AsyncTaskResponse>> getRunningTasks() {
    List<AsyncTaskResponse> runningTasks = asyncTaskService.getRunningTasks();
    return ResponseEntity.ok(runningTasks);
  }

  @PostMapping("/evaluate")
  @Operation(summary = "평가 실행 (동기)")
  public ResponseEntity<EvaluationExecuteResponse> executeEvaluation(
      @Valid @RequestBody EvaluationExecuteRequest request) {
    EvaluationExecuteResponse response =
        evaluationReportService.executeEvaluation(
            request.getReportName(), request.getRetrievalSize());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/evaluate-async")
  @Operation(summary = "평가 실행 (비동기)")
  public ResponseEntity<AsyncTaskStartResponse> executeEvaluationAsync(
      @Valid @RequestBody EvaluationExecuteAsyncRequest request) {
    Long taskId = asyncEvaluationService.startEvaluationExecution(request);
    return ResponseEntity.ok(
        AsyncTaskStartResponse.builder()
            .taskId(taskId)
            .message("평가 실행 작업이 시작되었습니다. 작업 ID: " + taskId)
            .build());
  }

  @GetMapping("/reports")
  @Operation(summary = "평가 리포트 리스트 조회")
  public ResponseEntity<List<EvaluationReportSummaryResponse>> getReports(
      @RequestParam(required = false) String keyword) {
    List<EvaluationReport> reports = evaluationReportService.getReportsByKeyword(keyword);
    List<EvaluationReportSummaryResponse> response =
        reports.stream()
            .map(
                r ->
                    EvaluationReportSummaryResponse.builder()
                        .id(r.getId())
                        .reportName(r.getReportName())
                        .totalQueries(r.getTotalQueries())
                        .averageNdcg(r.getAverageNdcg())
                        .totalRelevantDocuments(r.getTotalRelevantDocuments())
                        .totalRetrievedDocuments(r.getTotalRetrievedDocuments())
                        .totalCorrectDocuments(r.getTotalCorrectDocuments())
                        .createdAt(r.getCreatedAt())
                        .build())
            .toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/reports/{reportId}")
  @Operation(summary = "평가 리포트 상세 조회")
  public ResponseEntity<EvaluationReportDetailResponse> getReport(@PathVariable Long reportId) {
    EvaluationReportDetailResponse report = evaluationReportService.getReportDetail(reportId);
    if (report == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(report);
  }

  @DeleteMapping("/reports/{reportId}")
  @Operation(summary = "평가 리포트 단건 삭제")
  public ResponseEntity<Void> deleteReport(@PathVariable Long reportId) {
    boolean deleted = evaluationReportService.deleteReport(reportId);
    if (!deleted) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok().build();
  }
}
