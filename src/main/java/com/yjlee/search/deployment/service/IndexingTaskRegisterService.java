package com.yjlee.search.deployment.service;

import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.AsyncTaskService;
import com.yjlee.search.deployment.dto.IndexingRequest;
import com.yjlee.search.deployment.dto.IndexingStartResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingTaskRegisterService {
  private final AsyncTaskService asyncTaskService;

  @Transactional
  public IndexingStartResponse register(IndexingRequest request) {
    AsyncTask task =
        asyncTaskService.createTaskIfNotRunning(
            AsyncTaskType.INDEXING,
            "색인 작업 준비 중...",
            Map.of("description", request.getDescription()));
    return IndexingStartResponse.of(task.getId(), "색인 작업이 시작되었습니다");
  }
}
