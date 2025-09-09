package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yjlee.search.async.service.AsyncTaskService;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import com.yjlee.search.index.service.ProductIndexingService;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncIndexingService {

  @Value("${indexing.max-documents:}")
  private Integer maxDocumentsConfig;

  private final IndexEnvironmentRepository environmentRepository;
  private final DeploymentHistoryRepository historyRepository;
  private final ProductIndexingService productIndexingService;
  private final DictionaryDataDeploymentService dictionaryDeploymentService;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private final ElasticsearchClient elasticsearchClient;
  private final AsyncTaskService asyncTaskService;

  /** 비동기 색인 실행 */
  @Async("deploymentTaskExecutor")
  public void executeIndexingAsync(Long envId, String version, Long historyId, Long taskId) {
    try {
      log.info("비동기 색인 시작 - 버전: {}, taskId: {}", version, taskId);

      // 1. 사전 데이터 배포
      asyncTaskService.updateProgress(taskId, 10, "사전 데이터 배포 중...");
      deployDictionaries(version);

      // 2. 새 인덱스 생성
      asyncTaskService.updateProgress(taskId, 20, "인덱스 생성 중...");
      String newIndexName =
          elasticsearchIndexService.createNewIndex(version, DictionaryEnvironmentType.DEV);

      // 3. 상품 색인
      asyncTaskService.updateProgress(taskId, 30, "상품 색인 시작...");
      productIndexingService.setProgressCallback(
          (indexed, total) -> {
            int progress = 30 + (int) ((indexed * 60.0) / total);
            String message = String.format("상품 색인 중: %d/%d", indexed, total);
            asyncTaskService.updateProgress(taskId, progress, message);
          });

      int documentCount = indexProducts(newIndexName);

      // 4. 완료 처리
      asyncTaskService.updateProgress(taskId, 95, "색인 완료 처리 중...");
      completeIndexing(envId, historyId, newIndexName, version, documentCount);

      // 5. Task 완료
      IndexingResult result =
          IndexingResult.builder()
              .version(version)
              .documentCount(documentCount)
              .indexName(newIndexName)
              .build();
      asyncTaskService.completeTask(taskId, result);

      log.info("색인 완료 - 버전: {}, 문서: {}개", version, documentCount);

    } catch (Exception e) {
      log.error("색인 실패 - 버전: {}", version, e);
      asyncTaskService.failTask(taskId, "색인 실패: " + e.getMessage());
      failIndexing(envId, historyId);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeIndexing(
      Long envId, Long historyId, String indexName, String version, int count) {
    // 환경 업데이트
    IndexEnvironment env =
        environmentRepository
            .findById(envId)
            .orElseThrow(() -> new IllegalStateException("환경을 찾을 수 없습니다"));

    // 기존 인덱스 삭제 (옵션)
    deleteOldIndex(env.getIndexName());
    deleteOldIndex(env.getAutocompleteIndexName());

    env.setIndexName(indexName);
    env.setAutocompleteIndexName(
        elasticsearchIndexService.getAutocompleteIndexNameFromProductIndex(indexName));
    env.activate(version, (long) count);
    environmentRepository.save(env);

    // 이력 완료
    completeHistory(historyId, (long) count);

    // 실시간 동기화
    try {
      dictionaryDeploymentService.realtimeSyncAll(DictionaryEnvironmentType.DEV);
    } catch (Exception e) {
      log.warn("실시간 동기화 실패 (무시): {}", e.getMessage());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failIndexing(Long envId, Long historyId) {
    // 환경 상태는 변경하지 않음 (이력만 FAILED로 처리)
    // 이력 실패
    failHistory(historyId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deployDictionaries(String version) {
    try {
      dictionaryDeploymentService.deployAllToDev(version);
      log.info("사전 배포 완료 - 버전: {}", version);
    } catch (Exception e) {
      throw new RuntimeException("사전 배포 실패", e);
    }
  }

  private int indexProducts(String indexName) throws IOException {
    if (maxDocumentsConfig != null && maxDocumentsConfig > 0) {
      log.info("상품 색인: {} (최대 {}개)", indexName, maxDocumentsConfig);
      return productIndexingService.indexProductsToIndex(indexName, maxDocumentsConfig);
    } else {
      log.info("상품 색인: {} (전체)", indexName);
      return productIndexingService.indexProductsToIndex(indexName);
    }
  }

  private void deleteOldIndex(String indexName) {
    if (indexName != null) {
      try {
        elasticsearchClient.indices().delete(d -> d.index(indexName));
        log.info("기존 인덱스 삭제: {}", indexName);
      } catch (Exception e) {
        log.warn("인덱스 삭제 실패 (무시): {}", indexName);
      }
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeHistory(Long historyId, Long count) {
    historyRepository
        .findById(historyId)
        .ifPresent(
            history -> {
              history.complete(LocalDateTime.now(), count);
              historyRepository.save(history);
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failHistory(Long historyId) {
    historyRepository
        .findById(historyId)
        .ifPresent(
            history -> {
              history.fail();
              historyRepository.save(history);
            });
  }

  @Builder
  @Getter
  public static class IndexingResult {
    private String version;
    private int documentCount;
    private String indexName;
  }
}
