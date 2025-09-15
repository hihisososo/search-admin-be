package com.yjlee.search.async.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.TaskWorker;
import com.yjlee.search.evaluation.dto.GenerateCandidatesRequest;
import com.yjlee.search.evaluation.service.AsyncEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateGenerationTaskWorker implements TaskWorker {

  private final AsyncEvaluationService asyncEvaluationService;
  private final ObjectMapper objectMapper;

  @Override
  public AsyncTaskType getSupportedTaskType() {
    return AsyncTaskType.CANDIDATE_GENERATION;
  }

  @Override
  public void execute(AsyncTask task) {
    try {
      log.info("후보군 생성 작업 시작: taskId={}", task.getId());

      GenerateCandidatesRequest request = null;
      if (task.getParams() != null) {
        request = objectMapper.readValue(task.getParams(), GenerateCandidatesRequest.class);
      }

      if (request == null) {
        request = new GenerateCandidatesRequest();
      }

      asyncEvaluationService.executeCandidateGeneration(task.getId(), request);

    } catch (Exception e) {
      log.error("후보군 생성 작업 실행 중 오류: taskId={}", task.getId(), e);
      throw new RuntimeException("후보군 생성 작업 실패", e);
    }
  }
}
