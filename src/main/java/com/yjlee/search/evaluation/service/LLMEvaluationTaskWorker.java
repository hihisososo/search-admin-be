package com.yjlee.search.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.TaskWorker;
import com.yjlee.search.evaluation.dto.LLMEvaluationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LLMEvaluationTaskWorker implements TaskWorker {

  private final AsyncEvaluationService asyncEvaluationService;
  private final ObjectMapper objectMapper;

  @Override
  public AsyncTaskType getSupportedTaskType() {
    return AsyncTaskType.LLM_EVALUATION;
  }

  @Override
  public void execute(AsyncTask task) {
    try {
      log.info("LLM 평가 작업 시작: taskId={}", task.getId());

      LLMEvaluationRequest request = null;
      if (task.getParams() != null) {
        request = objectMapper.readValue(task.getParams(), LLMEvaluationRequest.class);
      }

      if (request == null) {
        request = new LLMEvaluationRequest();
      }

      asyncEvaluationService.executeLLMEvaluation(task.getId(), request);

    } catch (Exception e) {
      log.error("LLM 평가 작업 실행 중 오류: taskId={}", task.getId(), e);
      throw new RuntimeException("LLM 평가 작업 실패", e);
    }
  }
}
