package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.dto.GenerateCandidatesRequest;
import com.yjlee.search.evaluation.dto.GenerateQueriesRequest;
import com.yjlee.search.evaluation.dto.LLMEvaluationRequest;
import com.yjlee.search.evaluation.model.AsyncTask;
import com.yjlee.search.evaluation.model.AsyncTaskType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncEvaluationService {

  private final AsyncTaskService asyncTaskService;
  private final QueryGenerationService queryGenerationService;
  private final SearchBasedGroundTruthService groundTruthService;
  private final LLMCandidateEvaluationService llmEvaluationService;

  public Long startQueryGeneration(GenerateQueriesRequest request) {
    AsyncTask task = asyncTaskService.createTask(AsyncTaskType.QUERY_GENERATION, "쿼리 자동생성 준비 중...");

    generateQueriesAsync(task.getId(), request);
    return task.getId();
  }

  public Long startCandidateGeneration(GenerateCandidatesRequest request) {
    AsyncTask task =
        asyncTaskService.createTask(AsyncTaskType.CANDIDATE_GENERATION, "후보군 생성 준비 중...");

    generateCandidatesAsync(task.getId(), request);
    return task.getId();
  }

  public Long startLLMEvaluation(LLMEvaluationRequest request) {
    AsyncTask task = asyncTaskService.createTask(AsyncTaskType.LLM_EVALUATION, "LLM 자동평가 준비 중...");

    evaluateWithLLMAsync(task.getId(), request);
    return task.getId();
  }

  @Async("evaluationTaskExecutor")
  public void generateQueriesAsync(Long taskId, GenerateQueriesRequest request) {
    try {
      log.info("비동기 쿼리 생성 시작: taskId={}, count={}", taskId, request.getCount());

      asyncTaskService.updateProgress(taskId, 10, "쿼리 생성 시작...");

      // 실제 쿼리 생성 (진행상황 업데이트를 위해 수정된 버전 필요)
      List<String> generatedQueries = generateQueriesWithProgress(taskId, request.getCount());

      asyncTaskService.updateProgress(taskId, 90, "생성된 쿼리 저장 중...");

      // 결과 저장
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

      asyncTaskService.updateProgress(taskId, 10, "후보군 생성 시작...");

      if (Boolean.TRUE.equals(request.getGenerateForAllQueries())) {
        asyncTaskService.updateProgress(taskId, 30, "전체 쿼리에 대한 후보군 생성 중...");
        groundTruthService.generateCandidatesFromSearch();
      } else if (request.getQueryIds() != null && !request.getQueryIds().isEmpty()) {
        asyncTaskService.updateProgress(
            taskId, 30, String.format("선택된 %d개 쿼리에 대한 후보군 생성 중...", request.getQueryIds().size()));
        groundTruthService.generateCandidatesForSelectedQueries(request.getQueryIds());
      }

      asyncTaskService.updateProgress(taskId, 90, "후보군 생성 완료, 결과 정리 중...");

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

  @Async("llmTaskExecutor")
  public void evaluateWithLLMAsync(Long taskId, LLMEvaluationRequest request) {
    try {
      log.info("비동기 LLM 평가 시작: taskId={}", taskId);

      asyncTaskService.updateProgress(taskId, 10, "LLM 평가 시작...");

      if (Boolean.TRUE.equals(request.getEvaluateAllQueries())) {
        asyncTaskService.updateProgress(taskId, 30, "전체 쿼리의 후보군 LLM 평가 중...");
        llmEvaluationService.evaluateAllCandidates();
      } else if (request.getQueryIds() != null && !request.getQueryIds().isEmpty()) {
        asyncTaskService.updateProgress(
            taskId, 30, String.format("선택된 %d개 쿼리의 후보군 LLM 평가 중...", request.getQueryIds().size()));
        llmEvaluationService.evaluateCandidatesForQueries(request.getQueryIds());
      }

      asyncTaskService.updateProgress(taskId, 90, "LLM 평가 완료, 결과 정리 중...");

      LLMEvaluationResult result =
          LLMEvaluationResult.builder()
              .isEvaluateAllQueries(request.getEvaluateAllQueries())
              .queryIds(request.getQueryIds())
              .build();

      asyncTaskService.completeTask(taskId, result);
      log.info("비동기 LLM 평가 완료: taskId={}", taskId);

    } catch (Exception e) {
      log.error("비동기 LLM 평가 실패: taskId={}", taskId, e);
      asyncTaskService.failTask(taskId, "LLM 평가 실패: " + e.getMessage());
    }
  }

  private List<String> generateQueriesWithProgress(Long taskId, int count) {
    // 기존 쿼리 생성 로직에 진행상황 업데이트 추가
    asyncTaskService.updateProgress(taskId, 20, "상품 정보 수집 중...");

    asyncTaskService.updateProgress(taskId, 40, "LLM 쿼리 생성 중...");
    List<String> queries = queryGenerationService.generateRandomQueries(count);

    asyncTaskService.updateProgress(taskId, 80, "생성된 쿼리 검증 중...");

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
  public static class LLMEvaluationResult {
    private Boolean isEvaluateAllQueries;
    private List<Long> queryIds;
  }
}
