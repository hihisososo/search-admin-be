package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.dto.EvaluationExecuteAsyncRequest;
import com.yjlee.search.evaluation.dto.GenerateCandidatesRequest;
import com.yjlee.search.evaluation.dto.GenerateQueriesRequest;
import com.yjlee.search.evaluation.dto.LLMEvaluationRequest;
import com.yjlee.search.evaluation.dto.LLMQueryGenerateRequest;
import com.yjlee.search.evaluation.model.AsyncTask;
import com.yjlee.search.evaluation.model.AsyncTaskType;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AsyncEvaluationService {

  private final AsyncTaskService asyncTaskService;
  private final QueryGenerationService queryGenerationService;
  private final SearchBasedGroundTruthService groundTruthService;
  private final EvaluationQueryService evaluationQueryService;
  private final EvaluationReportService evaluationReportService;
  private final LLMCandidateEvaluationService llmCandidateEvaluationService;
  private final AsyncEvaluationService self;

  public AsyncEvaluationService(
      AsyncTaskService asyncTaskService,
      QueryGenerationService queryGenerationService,
      SearchBasedGroundTruthService groundTruthService,
      EvaluationQueryService evaluationQueryService,
      EvaluationReportService evaluationReportService,
      LLMCandidateEvaluationService llmCandidateEvaluationService,
      @Lazy AsyncEvaluationService self) {
    this.asyncTaskService = asyncTaskService;
    this.queryGenerationService = queryGenerationService;
    this.groundTruthService = groundTruthService;
    this.evaluationQueryService = evaluationQueryService;
    this.evaluationReportService = evaluationReportService;
    this.llmCandidateEvaluationService = llmCandidateEvaluationService;
    this.self = self;
  }

  private static final int FIXED_MIN_CANDIDATES = 30;

  public Long startLLMQueryGeneration(LLMQueryGenerateRequest request) {
    AsyncTask task =
        asyncTaskService.createTask(AsyncTaskType.QUERY_GENERATION, "LLM 쿼리 생성 준비 중...");
    self.generateLLMQueriesAsync(task.getId(), request);
    return task.getId();
  }

  @Async("evaluationTaskExecutor")
  public void generateLLMQueriesAsync(Long taskId, LLMQueryGenerateRequest request) {
    try {
      asyncTaskService.updateProgress(taskId, 10, "LLM 쿼리 생성 시작...");

      int target = request.getCount();
      List<String> accepted = new ArrayList<>();
      java.util.Set<String> tried = new java.util.HashSet<>();
      int round = 0;
      while (accepted.size() < target) {
        round++;
        int need = Math.max(5, (target - accepted.size()) * 3);
        List<String> pool = queryGenerationService.generateQueriesPreview(need);

        int acceptedThisRound = 0;
        for (String q : pool) {
          if (accepted.size() >= target) break;
          if (q == null || q.isBlank() || tried.contains(q)) continue;
          tried.add(q);
          Set<String> ids = groundTruthService.getCandidateIdsForQuery(q);
          // 50개 이상 300개 이하인 경우만 수용
          if (ids.size() >= FIXED_MIN_CANDIDATES && ids.size() <= 300) {
            EvaluationQuery eq = evaluationQueryService.createQuerySafely(q);
            generateCandidatesForOne(eq);
            accepted.add(q);
            acceptedThisRound++;
            asyncTaskService.updateProgress(
                taskId,
                Math.min(80, 10 + (accepted.size() * 60 / target)),
                String.format("생성/저장: %d/%d (round %d)", accepted.size(), target, round));
          } else if (ids.size() > 300) {
            log.debug("쿼리 '{}' 건너뜀 - 후보군 {}개로 300개 초과", q, ids.size());
          }
        }

        // 안전장치: 라운드가 계속 0개 수용이면 점진적으로 need를 키우며 재시도
        if (acceptedThisRound == 0) {
          asyncTaskService.updateProgress(
              taskId,
              Math.min(79, 10 + (accepted.size() * 60 / target)),
              String.format("라운드 %d에서 적합 쿼리 없음, 재시도", round));
        }

        // 무한 루프 방지 - 충분히 많은 라운드 수행 후에도 목표 미달이면 종료
        if (round > 50 && accepted.size() < target) {
          log.warn("LLM 쿼리 생성 목표 미달 - 요청: {}, 달성: {}", target, accepted.size());
          break;
        }
      }

      asyncTaskService.updateProgress(taskId, 90, "정리 중...");
      asyncTaskService.completeTask(
          taskId,
          QueryGenerationResult.builder()
              .generatedCount(accepted.size())
              .requestedCount(target)
              .queries(accepted)
              .build());
    } catch (Exception e) {
      asyncTaskService.failTask(taskId, "LLM 쿼리 생성 실패: " + e.getMessage());
    }
  }

  private void generateCandidatesForOne(EvaluationQuery eq) {
    try {
      self.generateCandidatesAsync(
          0L, GenerateCandidatesRequest.builder().queryIds(List.of(eq.getId())).build());
    } catch (Exception ignored) {
    }
  }

  public Long startQueryGeneration(GenerateQueriesRequest request) {
    AsyncTask task = asyncTaskService.createTask(AsyncTaskType.QUERY_GENERATION, "쿼리 자동생성 준비 중...");

    self.generateQueriesAsync(task.getId(), request);
    return task.getId();
  }

  public Long startCandidateGeneration(GenerateCandidatesRequest request) {
    AsyncTask task =
        asyncTaskService.createTask(AsyncTaskType.CANDIDATE_GENERATION, "후보군 생성 준비 중...");

    self.generateCandidatesAsync(task.getId(), request);
    return task.getId();
  }

  public Long startEvaluationExecution(EvaluationExecuteAsyncRequest request) {
    AsyncTask task =
        asyncTaskService.createTask(AsyncTaskType.EVALUATION_EXECUTION, "평가 실행 준비 중...");

    self.executeEvaluationAsync(task.getId(), request);
    return task.getId();
  }

  public Long startLLMCandidateEvaluation(LLMEvaluationRequest request) {
    AsyncTask task =
        asyncTaskService.createTask(AsyncTaskType.LLM_EVALUATION, "LLM 후보군 평가 준비 중...");

    self.evaluateLLMCandidatesAsync(task.getId(), request);
    return task.getId();
  }

  @Async("evaluationTaskExecutor")
  public void generateQueriesAsync(Long taskId, GenerateQueriesRequest request) {
    try {
      log.info("비동기 쿼리 생성 시작: taskId={}, count={}", taskId, request.getCount());

      asyncTaskService.updateProgress(taskId, 10, "쿼리 생성 시작...");

      List<String> generatedQueries = generateQueriesWithProgress(taskId, request.getCount());

      asyncTaskService.updateProgress(taskId, 90, "생성된 쿼리 저장 중...");

      QueryGenerationResult result =
          QueryGenerationResult.builder()
              .generatedCount(generatedQueries.size())
              .requestedCount(request.getCount())
              .queries(generatedQueries)
              .build();

      asyncTaskService.completeTask(taskId, result);
      log.info("비동기 쿼리 생성 완료: taskId={}, 생성된 개수={}", taskId, generatedQueries.size());

    } catch (Exception e) {
      log.error("비동기 쿼리 생성 실패: taskId={}", taskId, e);
      asyncTaskService.failTask(taskId, "쿼리 생성 실패: " + e.getMessage());
    }
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
      log.info("비동기 평가 실행 시작: taskId={}, reportName={}", taskId, request.getReportName());

      asyncTaskService.updateProgress(taskId, 10, "평가 실행 시작...");

      // 진행률 업데이트 콜백 생성
      ProgressCallback evaluationCallback =
          (progress, message) -> {
            asyncTaskService.updateProgress(taskId, progress, message);
          };

      com.yjlee.search.evaluation.dto.EvaluationExecuteResponse response =
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

  private List<String> generateQueriesWithProgress(Long taskId, int count) {
    asyncTaskService.updateProgress(taskId, 40, "LLM 쿼리 생성 중...");
    List<String> queries = queryGenerationService.generateRandomQueries(count);
    return queries;
  }

  // 결과 DTO 클래스들
  @lombok.Builder
  @lombok.Getter
  @lombok.AllArgsConstructor
  @lombok.NoArgsConstructor
  public static class QueryGenerationResult {
    private int generatedCount;
    private int requestedCount;
    private List<String> queries;
  }

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
