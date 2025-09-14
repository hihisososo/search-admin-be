package com.yjlee.search.deployment.service;

import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.AsyncTaskService;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.dto.DeploymentOperationResponse;
import com.yjlee.search.deployment.dto.DeploymentRequest;
import com.yjlee.search.deployment.helper.DeploymentHistoryHelper;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {

  private final IndexEnvironmentRepository environmentRepository;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private final DictionaryDataDeploymentService dictionaryDeploymentService;
  private final AsyncTaskService asyncTaskService;
  private final DeploymentHistoryHelper historyHelper;

  /** 배포 실행 (DEV → PROD) */
  @Transactional
  public DeploymentOperationResponse executeDeployment(DeploymentRequest request) {
    log.debug("배포 시작: {}", request.getDescription());

    // 환경 조회
    IndexEnvironment devEnv = getEnvironment(EnvironmentType.DEV);
    IndexEnvironment prodEnv = getEnvironment(EnvironmentType.PROD);

    // 배포 가능 검증
    validateDeployment(devEnv);

    // 배포 이력 생성
    DeploymentHistory history =
        historyHelper.createHistory(
            DeploymentHistory.DeploymentType.DEPLOYMENT,
            devEnv.getVersion(),
            request.getDescription());

    // 배포 실행
    try {
      performDeployment(devEnv, prodEnv, history.getId());
    } catch (Exception e) {
      historyHelper.updateHistoryStatus(history.getId(), false, null);
      throw new RuntimeException("배포 실패", e);
    }

    return DeploymentOperationResponse.builder()
        .message("배포가 완료되었습니다.")
        .version(devEnv.getVersion())
        .historyId(history.getId())
        .build();
  }

  private void validateDeployment(IndexEnvironment devEnv) {
    if (devEnv.getIndexStatus() != IndexEnvironment.IndexStatus.ACTIVE) {
      throw new IllegalStateException("개발 환경에 활성화된 색인이 없습니다.");
    }

    if (asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING)) {
      throw new IllegalStateException("개발 환경에서 색인이 진행 중입니다.");
    }
  }

  private void performDeployment(IndexEnvironment devEnv, IndexEnvironment prodEnv, Long historyId)
      throws Exception {

    log.info("배포 실행: {} → products-search", devEnv.getIndexName());

    // 기존 인덱스 정보 백업 (롤백 대비)
    String oldProductIndex = prodEnv.getIndexName();
    String oldAutocompleteIndex = prodEnv.getAutocompleteIndexName();

    // 1. 운영 사전 데이터 배포
    dictionaryDeploymentService.preDeployAll();

    // 2. 운영 환경 정보 업데이트
    prodEnv = updateProductionEnvironment(prodEnv, devEnv);

    // 3. 배포 후 사전 후처리
    dictionaryDeploymentService.postDeployAll();

    // 4. 개발 환경 초기화
    devEnv.reset();
    environmentRepository.save(devEnv);

    // 5. 개발 사전 데이터 초기화
    dictionaryDeploymentService.deleteAllByEnvironment(EnvironmentType.DEV);

    // 6. 배포 이력 완료
    historyHelper.updateHistoryStatus(historyId, true, prodEnv.getDocumentCount());

    // 7. 실시간 동기화
    dictionaryDeploymentService.realtimeSyncAll(EnvironmentType.PROD);

    // 8. Alias 업데이트 (DEV 인덱스를 PROD alias로 전환)
    log.info("Alias 업데이트 시작 - 인덱스: {}", prodEnv.getIndexName());
    if (!elasticsearchIndexService.indexExists(prodEnv.getIndexName())) {
      log.error("인덱스가 존재하지 않음: {}", prodEnv.getIndexName());
      throw new IllegalStateException("배포할 인덱스가 존재하지 않습니다: " + prodEnv.getIndexName());
    }
    elasticsearchIndexService.updateProductsSearchAlias(prodEnv.getIndexName());
    elasticsearchIndexService.updateAutocompleteSearchAlias(prodEnv.getAutocompleteIndexName());

    // 8. 기존 운영 인덱스 삭제 (맨 마지막)
    deleteOldIndex(oldProductIndex);
    deleteOldIndex(oldAutocompleteIndex);

    log.info("배포 완료: {}", prodEnv.getIndexName());
  }

  private IndexEnvironment updateProductionEnvironment(
      IndexEnvironment prodEnv, IndexEnvironment devEnv) {
    log.info("PROD 환경 업데이트 전 - indexName: {}", prodEnv.getIndexName());
    log.info("DEV 환경 정보 - indexName: {}", devEnv.getIndexName());

    prodEnv.setIndexName(devEnv.getIndexName());
    prodEnv.setAutocompleteIndexName(devEnv.getAutocompleteIndexName());
    prodEnv.setVersion(devEnv.getVersion());
    prodEnv.setDocumentCount(devEnv.getDocumentCount());
    prodEnv.setIndexStatus(IndexEnvironment.IndexStatus.ACTIVE);
    prodEnv.setIndexDate(LocalDateTime.now());

    IndexEnvironment saved = environmentRepository.save(prodEnv);
    log.info("PROD 환경 업데이트 후 - indexName: {}", saved.getIndexName());
    return saved;
  }

  private void deleteOldIndex(String indexName) {
    if (indexName != null) {
      try {
        elasticsearchIndexService.deleteIndexIfExists(indexName);
        log.info("기존 인덱스 삭제: {}", indexName);
      } catch (Exception e) {
        log.warn("인덱스 삭제 실패 (무시): {}", e.getMessage());
      }
    }
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
}
