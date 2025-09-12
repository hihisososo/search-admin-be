package com.yjlee.search.evaluation.service;

import com.yjlee.search.async.dto.AsyncTaskStartResponse;
import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.AsyncTaskService;
import com.yjlee.search.evaluation.dto.EvaluationExecuteAsyncRequest;
import com.yjlee.search.evaluation.dto.EvaluationExecuteResponse;
import com.yjlee.search.evaluation.dto.GenerateCandidatesRequest;
import com.yjlee.search.evaluation.dto.LLMEvaluationRequest;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.search.dto.SearchMode;
import com.yjlee.search.search.service.VectorSearchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AsyncEvaluationService {

  private final AsyncTaskService asyncTaskService;
  private final SearchBasedGroundTruthService groundTruthService;
  private final EvaluationQueryService evaluationQueryService;
  private final EvaluationReportService evaluationReportService;
  private final LLMCandidateEvaluationService llmCandidateEvaluationService;
  private final AsyncEvaluationService self;
  private final VectorSearchService vectorSearchService;

  public AsyncEvaluationService(
      AsyncTaskService asyncTaskService,
      SearchBasedGroundTruthService groundTruthService,
      EvaluationQueryService evaluationQueryService,
      EvaluationReportService evaluationReportService,
      LLMCandidateEvaluationService llmCandidateEvaluationService,
      VectorSearchService vectorSearchService,
      @Lazy AsyncEvaluationService self) {
    this.asyncTaskService = asyncTaskService;
    this.groundTruthService = groundTruthService;
    this.evaluationQueryService = evaluationQueryService;
    this.evaluationReportService = evaluationReportService;
    this.llmCandidateEvaluationService = llmCandidateEvaluationService;
    this.vectorSearchService = vectorSearchService;
    this.self = self;
  }

  public Long startCandidateGeneration(GenerateCandidatesRequest request) {
    AsyncTask task =
        asyncTaskService.createTask(AsyncTaskType.CANDIDATE_GENERATION, "후보군 생성 준비 중...");

    self.generateCandidatesAsync(task.getId(), request);
    return task.getId();
  }

  public AsyncTaskStartResponse startCandidateGenerationWithResponse(
      GenerateCandidatesRequest request) {
    Long taskId = startCandidateGeneration(request);
    return AsyncTaskStartResponse.builder()
        .taskId(taskId)
        .message("후보군 생성 작업이 시작되었습니다. 작업 ID: " + taskId)
        .build();
  }

  public Long startEvaluationExecution(EvaluationExecuteAsyncRequest request) {
    AsyncTask task =
        asyncTaskService.createTask(AsyncTaskType.EVALUATION_EXECUTION, "평가 실행 준비 중...");

    self.executeEvaluationAsync(task.getId(), request);
    return task.getId();
  }

  public AsyncTaskStartResponse startEvaluationExecutionWithResponse(
      EvaluationExecuteAsyncRequest request) {
    Long taskId = startEvaluationExecution(request);
    return AsyncTaskStartResponse.builder()
        .taskId(taskId)
        .message("평가 실행 작업이 시작되었습니다. 작업 ID: " + taskId)
        .build();
  }

  public Long startLLMCandidateEvaluation(LLMEvaluationRequest request) {
    AsyncTask task =
        asyncTaskService.createTask(AsyncTaskType.LLM_EVALUATION, "LLM 후보군 평가 준비 중...");

    self.evaluateLLMCandidatesAsync(task.getId(), request);
    return task.getId();
  }

  public AsyncTaskStartResponse startLLMCandidateEvaluationWithResponse(
      LLMEvaluationRequest request) {
    Long taskId = startLLMCandidateEvaluation(request);
    return AsyncTaskStartResponse.builder()
        .taskId(taskId)
        .message("LLM 평가 작업이 시작되었습니다. 작업 ID: " + taskId)
        .build();
  }

  @Async("evaluationTaskExecutor")
  public void generateCandidatesAsync(Long taskId, GenerateCandidatesRequest request) {
    try {
      log.info("비동기 후보군 생성 시작: taskId={}", taskId);

      asyncTaskService.updateProgress(taskId, 5, "후보군 생성 초기화...");

      if (Boolean.TRUE.equals(request.getGenerateForAllQueries())) {
        asyncTaskService.updateProgress(taskId, 10, "전체 쿼리에 대한 후보군 생성 준비 중...");
        groundTruthService.generateCandidatesFromSearch(
            (done, total) -> {
              // 10%에서 90%까지 사용 (80% 구간)
              int progress = 10 + (int) Math.floor((double) done / Math.max(1, total) * 80);
              asyncTaskService.updateProgress(
                  taskId, progress, String.format("후보군 생성 진행: %d/%d 쿼리 완료", done, total));
            });
      } else if (request.getQueryIds() != null && !request.getQueryIds().isEmpty()) {
        int total = request.getQueryIds().size();
        asyncTaskService.updateProgress(
            taskId, 10, String.format("선택된 %d개 쿼리에 대한 후보군 생성 준비 중...", total));
        groundTruthService.generateCandidatesForSelectedQueries(
            request.getQueryIds(),
            (done, t) -> {
              // 10%에서 90%까지 사용 (80% 구간)
              int progress = 10 + (int) Math.floor((double) done / Math.max(1, t) * 80);
              asyncTaskService.updateProgress(
                  taskId, progress, String.format("후보군 생성 진행: %d/%d 쿼리 완료", done, t));
            });
      }

      asyncTaskService.updateProgress(taskId, 95, "후보군 생성 완료, 결과 정리 중...");

      CandidateGenerationResult result =
          CandidateGenerationResult.builder()
              .isGenerateForAllQueries(request.getGenerateForAllQueries())
              .queryIds(request.getQueryIds())
              .build();

      asyncTaskService.completeTask(taskId, result);
      log.info("비동기 후보군 생성 완료: taskId={}", taskId);

    } catch (Exception e) {
      log.error("비동기 후보군 생성 실패: taskId={}", taskId, e);
      asyncTaskService.failTask(taskId, "후보군 생성 실패: " + e.getMessage());
    }
  }

  @Async("evaluationTaskExecutor")
  public void executeEvaluationAsync(Long taskId, EvaluationExecuteAsyncRequest request) {
    try {
      log.info(
          "비동기 평가 실행 시작: taskId={}, reportName={}, searchMode={}",
          taskId,
          request.getReportName(),
          request.getSearchMode());

      asyncTaskService.updateProgress(taskId, 5, "평가 실행 준비 중...");

      // 하이브리드 모드일 경우 임베딩 사전 캐싱
      if (SearchMode.HYBRID_RRF.equals(request.getSearchMode())) {
        asyncTaskService.updateProgress(taskId, 10, "임베딩 사전 캐싱 시작...");
        precacheEmbeddings(taskId);
        asyncTaskService.updateProgress(taskId, 20, "임베딩 캐싱 완료, 평가 시작...");
      } else {
        asyncTaskService.updateProgress(taskId, 10, "평가 실행 시작...");
      }

      // 진행률 업데이트 콜백 생성 (하이브리드 모드는 20부터 시작)
      int startProgress = SearchMode.HYBRID_RRF.equals(request.getSearchMode()) ? 20 : 10;
      int endProgress = 90;
      ProgressCallback evaluationCallback =
          (progress, message) -> {
            // 진행률을 조정하여 전체 범위에 맞춤
            int adjustedProgress =
                startProgress + (int) ((endProgress - startProgress) * progress / 100.0);
            asyncTaskService.updateProgress(taskId, adjustedProgress, message);
          };

      EvaluationExecuteResponse response =
          evaluationReportService.executeEvaluation(
              request.getReportName(),
              request.getSearchMode(),
              request.getRrfK(),
              request.getHybridTopK(),
              evaluationCallback);

      asyncTaskService.updateProgress(taskId, 90, "평가 완료, 결과 정리 중...");

      EvaluationExecutionResult result =
          EvaluationExecutionResult.builder()
              .reportName(request.getReportName())
              .reportId(response.getReportId())
              .totalQueries(response.getTotalQueries())
              .recall300(response.getRecall300())
              .precision20(response.getPrecision20())
              .build();

      asyncTaskService.completeTask(taskId, result);
      log.info("비동기 평가 실행 완료: taskId={}, reportId={}", taskId, response.getReportId());

    } catch (Exception e) {
      log.error("비동기 평가 실행 실패: taskId={}", taskId, e);
      asyncTaskService.failTask(taskId, "평가 실행 실패: " + e.getMessage());
    }
  }

  @Async("evaluationTaskExecutor")
  public void evaluateLLMCandidatesAsync(Long taskId, LLMEvaluationRequest request) {
    try {
      log.info("비동기 LLM 후보군 평가 시작: taskId={}", taskId);

      asyncTaskService.updateProgress(taskId, 10, "LLM 평가 시작...");

      // 진행률 업데이트 콜백 생성
      ProgressCallback progressCallback =
          (progress, message) -> {
            asyncTaskService.updateProgress(taskId, progress, message);
          };

      if (Boolean.TRUE.equals(request.getEvaluateAllQueries())) {
        asyncTaskService.updateProgress(taskId, 30, "전체 쿼리 LLM 평가 진행 중...");
        llmCandidateEvaluationService.evaluateAllCandidates(progressCallback);
      } else if (request.getQueryIds() != null && !request.getQueryIds().isEmpty()) {
        int total = request.getQueryIds().size();
        asyncTaskService.updateProgress(
            taskId, 30, String.format("선택된 %d개 쿼리 LLM 평가 진행 중...", total));
        llmCandidateEvaluationService.evaluateCandidatesForQueries(
            request.getQueryIds(), progressCallback);
      }

      asyncTaskService.updateProgress(taskId, 90, "LLM 평가 완료, 결과 정리 중...");

      LLMEvaluationResult result =
          LLMEvaluationResult.builder()
              .evaluateAllQueries(request.getEvaluateAllQueries())
              .queryIds(request.getQueryIds())
              .build();

      asyncTaskService.completeTask(taskId, result);
      log.info("비동기 LLM 후보군 평가 완료: taskId={}", taskId);

    } catch (Exception e) {
      log.error("비동기 LLM 후보군 평가 실패: taskId={}", taskId, e);
      asyncTaskService.failTask(taskId, "LLM 평가 실패: " + e.getMessage());
    }
  }

  /** 하이브리드 검색 평가 전 모든 쿼리에 대한 임베딩 사전 캐싱 */
  private void precacheEmbeddings(Long taskId) {
    try {
      // 모든 평가 쿼리 가져오기
      List<EvaluationQuery> allQueries = evaluationQueryService.getAllQueries();
      log.info("임베딩 사전 캐싱 시작: {}개 쿼리", allQueries.size());

      // 병렬로 임베딩 생성 (10개씩 배치 처리)
      int batchSize = 10;
      List<CompletableFuture<Void>> futures = new ArrayList<>();

      for (int i = 0; i < allQueries.size(); i += batchSize) {
        int start = i;
        int end = Math.min(i + batchSize, allQueries.size());
        List<EvaluationQuery> batch = allQueries.subList(start, end);

        CompletableFuture<Void> future =
            CompletableFuture.runAsync(
                () -> {
                  for (EvaluationQuery q : batch) {
                    try {
                      // 임베딩 생성 (캐시에 저장됨)
                      vectorSearchService.getQueryEmbedding(q.getQuery());
                    } catch (Exception e) {
                      log.warn("임베딩 생성 실패 (쿼리: {}): {}", q.getQuery(), e.getMessage());
                    }
                  }
                });

        futures.add(future);

        // 진행률 업데이트 (10-20% 구간)
        int progress = 10 + (int) ((end * 10.0) / allQueries.size());
        asyncTaskService.updateProgress(
            taskId, progress, String.format("임베딩 캐싱 중: %d/%d", end, allQueries.size()));
      }

      // 모든 배치 완료 대기
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

      log.info("임베딩 사전 캐싱 완료: 캐시 크기 {}", vectorSearchService.getCacheSize());

    } catch (Exception e) {
      log.error("임베딩 사전 캐싱 중 오류 발생", e);
      // 오류가 발생해도 평가는 계속 진행 (각 쿼리 실행 시 임베딩 생성됨)
    }
  }

  // 결과 DTO 클래스들
  @lombok.Builder
  @lombok.Getter
  @lombok.AllArgsConstructor
  @lombok.NoArgsConstructor
  public static class CandidateGenerationResult {
    private Boolean isGenerateForAllQueries;
    private List<Long> queryIds;
  }

  @lombok.Builder
  @lombok.Getter
  @lombok.AllArgsConstructor
  @lombok.NoArgsConstructor
  public static class EvaluationExecutionResult {
    private String reportName;
    private Long reportId;
    private int totalQueries;
    private Double recall300;
    private Double precision20;
  }

  @lombok.Builder
  @lombok.Getter
  @lombok.AllArgsConstructor
  @lombok.NoArgsConstructor
  public static class LLMEvaluationResult {
    private Boolean evaluateAllQueries;
    private List<Long> queryIds;
  }
}
