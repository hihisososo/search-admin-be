package com.yjlee.search.async.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.TaskWorker;
import com.yjlee.search.evaluation.dto.EvaluationExecuteAsyncRequest;
import com.yjlee.search.evaluation.service.AsyncEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationExecutionTaskWorker implements TaskWorker {

  private final AsyncEvaluationService asyncEvaluationService;
  private final ObjectMapper objectMapper;

  @Override
  public AsyncTaskType getSupportedTaskType() {
    return AsyncTaskType.EVALUATION_EXECUTION;
  }

  @Override
  public void execute(AsyncTask task) {
    try {
      log.info("평가 실행 작업 시작: taskId={}", task.getId());

      EvaluationExecuteAsyncRequest request = null;
      if (task.getParams() != null) {
        request = objectMapper.readValue(task.getParams(), EvaluationExecuteAsyncRequest.class);
      }

      if (request == null) {
        request = new EvaluationExecuteAsyncRequest();
      }

      asyncEvaluationService.executeEvaluationTask(task.getId(), request);

    } catch (Exception e) {
      log.error("평가 실행 작업 실행 중 오류: taskId={}", task.getId(), e);
      throw new RuntimeException("평가 실행 작업 실패", e);
    }
  }
}
