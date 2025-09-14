package com.yjlee.search.deployment.service;

import com.yjlee.search.async.service.AsyncTaskService;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.domain.IndexingResult;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import com.yjlee.search.index.provider.IndexNameProvider;
import com.yjlee.search.index.service.ProductIndexingService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncIndexingService {

  private static final int PROGRESS_INIT = 10;
  private static final int PROGRESS_INDEXING_START = 30;
  private static final int PROGRESS_INDEXING_END = 90;
  private static final int PROGRESS_COMPLETE = 95;

  private final IndexEnvironmentRepository environmentRepository;
  private final DeploymentHistoryRepository historyRepository;
  private final ProductIndexingService productIndexingService;
  private final DictionaryDataDeploymentService dictionaryDeploymentService;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private final AsyncTaskService asyncTaskService;
  private final IndexNameProvider indexNameProvider;

  @Async("deploymentTaskExecutor")
  public void executeIndexingAsync(Long envId, String version, Long historyId, Long taskId) {
    try {
      // 사전 데이터 배포
      asyncTaskService.updateProgress(taskId, PROGRESS_INIT, "사전 데이터 배포 중...");
      dictionaryDeploymentService.preIndexingAll();

      // 인덱스 생성
      asyncTaskService.updateProgress(taskId, PROGRESS_INIT + 10, "인덱스 생성 중...");
      elasticsearchIndexService.createNewIndex(version, EnvironmentType.DEV);

      // 상품 색인
      asyncTaskService.updateProgress(taskId, PROGRESS_INDEXING_START, "상품 색인 시작...");
      productIndexingService.setProgressCallback(
          (indexed, total) -> {
            int range = PROGRESS_INDEXING_END - PROGRESS_INDEXING_START;
            int progress = PROGRESS_INDEXING_START + (int) ((indexed * range) / total);
            String message = String.format("상품 색인 중: %d/%d", indexed, total);
            asyncTaskService.updateProgress(taskId, progress, message);
          });
      int documentCount = productIndexingService.indexProducts(version);

      // 색인 후 사전 후처리
      asyncTaskService.updateProgress(taskId, 95, "색인 후 사전 동기화 중...");
      dictionaryDeploymentService.postIndexingAll();

      // 색인 완료 처리
      asyncTaskService.updateProgress(taskId, PROGRESS_COMPLETE, "색인 완료 처리 중...");
      finalizeAndUpdateEnvironment(envId, historyId, version, documentCount);
      asyncTaskService.completeTask(
          taskId,
          IndexingResult.builder()
              .version(version)
              .documentCount(documentCount)
              .indexName(indexNameProvider.getProductIndexName(version))
              .build());
    } catch (Exception e) {
      log.error("색인 실패 - 버전: {}", version, e);
      asyncTaskService.failTask(taskId, "색인 실패: " + e.getMessage());
      updateHistory(historyId, false, null);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void finalizeAndUpdateEnvironment(Long envId, Long historyId, String version, int count) {
    IndexEnvironment env =
        environmentRepository
            .findById(envId)
            .orElseThrow(() -> new IllegalStateException("환경을 찾을 수 없습니다"));

    // 환경 업데이트 (인덱스명은 이미 prepareIndexing에서 설정됨)
    env.activate(version, (long) count);
    environmentRepository.save(env);

    // 사전 데이터 실시간 동기화
    dictionaryDeploymentService.realtimeSyncAll(EnvironmentType.DEV);

    // 히스토리 업데이트
    updateHistory(historyId, true, (long) count);
  }

  @Transactional
  public void updateHistory(Long historyId, boolean success, Long count) {
    historyRepository
        .findById(historyId)
        .ifPresent(
            history -> {
              if (success) {
                history.complete(LocalDateTime.now(), count);
              } else {
                history.fail();
              }
              historyRepository.save(history);
            });
  }
}
