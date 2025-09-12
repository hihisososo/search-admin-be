package com.yjlee.search.evaluation.controller;

import com.yjlee.search.async.dto.AsyncTaskStartResponse;
import com.yjlee.search.evaluation.dto.EvaluationExecuteAsyncRequest;
import com.yjlee.search.evaluation.dto.EvaluationReportDetailResponse;
import com.yjlee.search.evaluation.dto.EvaluationReportSummaryResponse;
import com.yjlee.search.evaluation.service.AsyncEvaluationService;
import com.yjlee.search.evaluation.service.EvaluationReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    return ResponseEntity.ok(asyncEvaluationService.startEvaluationExecutionWithResponse(request));
  }

  @GetMapping("/reports")
  @Operation(summary = "평가 리포트 리스트 조회")
  public ResponseEntity<List<EvaluationReportSummaryResponse>> getReports(
      @RequestParam(required = false) String keyword) {
    return ResponseEntity.ok(evaluationReportService.getReportSummariesByKeyword(keyword));
  }

  @GetMapping("/reports/{reportId}")
  @Operation(summary = "평가 리포트 상세 조회")
  public ResponseEntity<EvaluationReportDetailResponse> getReport(@PathVariable Long reportId) {
    return ResponseEntity.ok(evaluationReportService.getReportDetail(reportId));
  }

  @DeleteMapping("/reports/{reportId}")
  @Operation(summary = "평가 리포트 단건 삭제")
  public ResponseEntity<Void> deleteReport(@PathVariable Long reportId) {
    evaluationReportService.deleteReport(reportId);
    return ResponseEntity.ok().build();
  }
}
