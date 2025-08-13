package com.yjlee.search.evaluation.service;

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
  private final EvaluationQueryService evaluationQueryService;

  public Long startLLMQueryGeneration(LLMQueryGenerateRequest request) {
    AsyncTask task =
        asyncTaskService.createTask(AsyncTaskType.QUERY_GENERATION, "LLM 쿼리 생성 준비 중...");
    generateLLMQueriesAsync(task.getId(), request);
    return task.getId();
  }

  @Async("evaluationTaskExecutor")
  public void generateLLMQueriesAsync(Long taskId, LLMQueryGenerateRequest request) {
    try {
      asyncTaskService.updateProgress(taskId, 10, "LLM 쿼리 생성 시작...");

      int target = request.getCount();
      int minC = request.getMinCandidates() != null ? request.getMinCandidates() : 60;
      int maxC = request.getMaxCandidates() != null ? request.getMaxCandidates() : 200;

      List<String> pool =
          (request.getCategory() == null || request.getCategory().isBlank())
              ? queryGenerationService.generateQueriesPreview(target * 3)
              : queryGenerationService.generateQueriesPreviewWithCategory(
                  target * 3, request.getCategory());

      List<String> accepted = new ArrayList<>();

      for (String q : pool) {
        if (accepted.size() >= target) break;
        Set<String> ids = groundTruthService.getCandidateIdsForQuery(q);
        int c = ids.size();
        if (c >= minC && c <= maxC) {
          EvaluationQuery eq = evaluationQueryService.createQuerySafely(q);
          generateCandidatesForOne(eq);
          accepted.add(q);
          asyncTaskService.updateProgress(
              taskId,
              Math.min(80, 10 + (accepted.size() * 60 / target)),
              String.format("생성/저장: %d/%d", accepted.size(), target));
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
      generateCandidatesAsync(
          0L, GenerateCandidatesRequest.builder().queryIds(List.of(eq.getId())).build());
    } catch (Exception ignored) {
    }
  }

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
