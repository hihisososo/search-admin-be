package com.yjlee.search.evaluation.controller;

import com.yjlee.search.async.dto.AsyncTaskStartResponse;
import com.yjlee.search.evaluation.dto.EvaluationExecuteAsyncRequest;
import com.yjlee.search.evaluation.dto.EvaluationReportDetailResponse;
import com.yjlee.search.evaluation.dto.EvaluationReportSummaryResponse;
import com.yjlee.search.evaluation.model.EvaluationReport;
import com.yjlee.search.evaluation.service.AsyncEvaluationService;
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
                        .averagePrecision20(r.getAveragePrecision20())
                        .averageRecall300(r.getAverageRecall300())
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
