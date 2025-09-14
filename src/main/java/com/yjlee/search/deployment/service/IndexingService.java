package com.yjlee.search.deployment.service;

import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.AsyncTaskService;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.dto.IndexingRequest;
import com.yjlee.search.deployment.dto.IndexingStartResponse;
import com.yjlee.search.deployment.helper.DeploymentHistoryHelper;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.index.provider.IndexNameProvider;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {

  private final IndexEnvironmentRepository environmentRepository;
  private final AsyncTaskService asyncTaskService;
  private final AsyncIndexingService asyncIndexingService;
  private final DeploymentHistoryHelper historyHelper;
  private final IndexNameProvider indexNameProvider;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private static final Lock indexingLock = new ReentrantLock();

  @Transactional
  public IndexingStartResponse executeIndexing(IndexingRequest request) {
    // 즉시 락 획득 시도, 실패하면 다른 작업이 진행 중
    if (!indexingLock.tryLock()) {
      throw new IllegalStateException("색인이 이미 진행 중입니다.");
    }

    try {
      log.info("색인 시작: {}", request.getDescription());

      IndexingContext context = prepareIndexing(request);

      startAsyncExecution(context);

      return IndexingStartResponse.builder()
          .taskId(context.getTaskId())
          .message("색인 작업이 시작되었습니다")
          .build();
    } finally {
      indexingLock.unlock();
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IndexingContext prepareIndexing(IndexingRequest request) {
    // 개발 환경 조회
    IndexEnvironment devEnv = getEnvironment(EnvironmentType.DEV);

    if (asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING)) {
      throw new IllegalStateException("색인이 이미 진행 중입니다.");
    }

    // 기존 인덱스 삭제 (reset 전에 수행)
    cleanupOldIndices(devEnv);

    // 버전 생성 및 이력 저장
    String version = historyHelper.generateVersion();
    DeploymentHistory history =
        historyHelper.createHistory(
            DeploymentHistory.DeploymentType.INDEXING, version, request.getDescription());

    // 색인 이름 저장
    String indexName = indexNameProvider.getProductIndexName(version);
    String autoCompleteIndexname = indexNameProvider.getAutocompleteIndexName(version);
    String synonymSetName = indexNameProvider.getSynonymSetName(version);
    devEnv.reset();
    devEnv.updatePrepareIndexing(indexName, autoCompleteIndexname, synonymSetName, version);
    environmentRepository.save(devEnv);

    // AsyncTask 생성
    String initialMessage = String.format("색인 준비 중... (버전: %s)", version);
    AsyncTask task = asyncTaskService.createTask(AsyncTaskType.INDEXING, initialMessage);

    return new IndexingContext(devEnv.getId(), version, history.getId(), task.getId());
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void startAsyncExecution(IndexingContext context) {
    asyncIndexingService.executeIndexingAsync(
        context.getEnvId(), context.getVersion(), context.getHistoryId(), context.getTaskId());
  }

  private IndexEnvironment getEnvironment(EnvironmentType type) {
    return environmentRepository
        .findByEnvironmentType(type)
        .orElseGet(
            () -> {
              log.info("{} 환경이 없어서 새로 생성합니다.", type);
              return environmentRepository.save(
                  IndexEnvironment.builder()
                      .environmentType(type)
                      .indexStatus(IndexEnvironment.IndexStatus.INACTIVE)
                      .documentCount(0L)
                      .build());
            });
  }

  private void cleanupOldIndices(IndexEnvironment env) {
    // 이전 색인의 인덱스가 있다면 삭제
    if (env.getIndexName() != null) {
      try {
        elasticsearchIndexService.deleteIndexIfExists(env.getIndexName());
        log.info("기존 상품 인덱스 삭제: {}", env.getIndexName());
      } catch (IOException e) {
        log.warn("기존 상품 인덱스 삭제 실패: {}", env.getIndexName(), e);
      }
    }

    if (env.getAutocompleteIndexName() != null) {
      try {
        elasticsearchIndexService.deleteIndexIfExists(env.getAutocompleteIndexName());
        log.info("기존 자동완성 인덱스 삭제: {}", env.getAutocompleteIndexName());
      } catch (IOException e) {
        log.warn("기존 자동완성 인덱스 삭제 실패: {}", env.getAutocompleteIndexName(), e);
      }
    }
  }

  public static class IndexingContext {
    private final Long envId;
    private final String version;
    private final Long historyId;
    private final Long taskId;

    public IndexingContext(Long envId, String version, Long historyId, Long taskId) {
      this.envId = envId;
      this.version = version;
      this.historyId = historyId;
      this.taskId = taskId;
    }

    public Long getEnvId() {
      return envId;
    }

    public String getVersion() {
      return version;
    }

    public Long getHistoryId() {
      return historyId;
    }

    public Long getTaskId() {
      return taskId;
    }
  }
}
