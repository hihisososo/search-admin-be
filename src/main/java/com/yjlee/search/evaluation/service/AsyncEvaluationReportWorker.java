package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.dto.EvaluationExecuteResponse;
import com.yjlee.search.search.dto.SearchMode;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncEvaluationReportWorker {

  private final EvaluationReportService evaluationReportService;

  @Async("evaluationTaskExecutor")
  public CompletableFuture<EvaluationExecuteResponse.QueryEvaluationDetail> evaluateQueryAsync(
      String query) {
    return evaluateQueryAsync(query, SearchMode.KEYWORD_ONLY, 60, 100);
  }

  @Async("evaluationTaskExecutor")
  public CompletableFuture<EvaluationExecuteResponse.QueryEvaluationDetail> evaluateQueryAsync(
      String query, SearchMode searchMode, Integer rrfK, Integer hybridTopK) {
    try {
      EvaluationExecuteResponse.QueryEvaluationDetail detail =
          evaluationReportService.evaluateQuery(query, searchMode, rrfK, hybridTopK);
      return CompletableFuture.completedFuture(detail);
    } catch (Exception e) {
      log.warn("⚠️ 쿼리 '{}' 평가 실패", query, e);
      return CompletableFuture.completedFuture(null);
    }
  }
}
