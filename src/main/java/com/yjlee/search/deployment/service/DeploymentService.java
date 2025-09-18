package com.yjlee.search.deployment.service;

import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.AsyncTaskService;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.domain.DeploymentContext;
import com.yjlee.search.deployment.dto.DeploymentOperationResponse;
import com.yjlee.search.deployment.dto.DeploymentRequest;
import com.yjlee.search.deployment.enums.DeploymentType;
import com.yjlee.search.deployment.enums.IndexStatus;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import com.yjlee.search.index.provider.IndexNameProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {

  private final IndexEnvironmentService environmentService;
  private final DictionaryDataDeploymentService dictionaryDataDeploymentService;
  private final ElasticsearchIndexAliasService elasticsearchIndexAliasService;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private final DeploymentHistoryService historyService;
  private final AsyncTaskService asyncTaskService;
  private final IndexNameProvider indexNameProvider;

  @Transactional
  public DeploymentOperationResponse executeDeployment(DeploymentRequest request) {
    log.info("배포 시작: {}", request.getDescription());

    DeploymentContext context = prepareDeploymentContext();
    validateDeploymentContext(context);

    DeploymentHistory history =
        historyService.createHistory(
            DeploymentType.DEPLOYMENT, context.getDevVersion(), request.getDescription());
    context.setHistoryId(history.getId());

    try {
      performDeployment(context);

      return DeploymentOperationResponse.success(context, history);
    } catch (Exception e) {
      historyService.updateHistoryStatus(history.getId(), false, null);
      throw new RuntimeException("배포 실패", e);
    }
  }

  public DeploymentContext prepareDeploymentContext() {
    var devEnv = environmentService.getEnvironment(EnvironmentType.DEV);
    var prodEnv = environmentService.getOrCreateEnvironment(EnvironmentType.PROD);
    return DeploymentContext.from(devEnv, prodEnv);
  }

  private void validateDeploymentContext(DeploymentContext context) {
    var devEnv = environmentService.getEnvironment(EnvironmentType.DEV);
    validateEnvironmentStatus(devEnv);
    validateNoRunningTasks();
  }

  private void validateEnvironmentStatus(IndexEnvironment environment) {
    if (environment == null) {
      throw new IllegalStateException("환경 정보가 없습니다.");
    }

    if (environment.getIndexStatus() != IndexStatus.ACTIVE) {
      throw new IllegalStateException(
          String.format("%s 환경에 활성화된 색인이 없습니다.", environment.getEnvironmentType()));
    }

    if (environment.getIndexName() == null || environment.getIndexName().isEmpty()) {
      throw new IllegalStateException(
          String.format("%s 환경에 인덱스가 설정되지 않았습니다.", environment.getEnvironmentType()));
    }
  }

  private void validateNoRunningTasks() {
    if (asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING)) {
      throw new IllegalStateException("개발 환경에서 색인이 진행 중입니다. 색인 완료 후 배포하세요.");
    }
  }

  private void performDeployment(DeploymentContext context) {
    log.info("배포 프로세스 시작: {} → products-search", context.getDevIndexName());

    try {
      // 환경 전환
      updateEnvironments(context);

      // 히스토리 업데이트
      historyService.updateHistoryStatus(
          context.getHistoryId(), true, context.getDevDocumentCount());

      // Alias 업데이트
      elasticsearchIndexAliasService.updateAliases(
          context.getDevIndexName(), indexNameProvider.getProductsSearchAlias(),
          context.getDevAutocompleteIndexName(), indexNameProvider.getAutocompleteSearchAlias());

      // 이전 인덱스 삭제
      elasticsearchIndexService.deleteIndexIfExists(context.getPreviousProdIndexName());
      elasticsearchIndexService.deleteIndexIfExists(context.getPreviousProdAutocompleteIndexName());

      context.markCompleted();
      log.info("배포 완료: {}", context.getDevIndexName());
    } catch (Exception e) {
      log.error("배포 중 오류 발생", e);
      throw e;
    }
  }

  public void updateEnvironments(DeploymentContext context) {
    log.info("환경 전환 시작: DEV → PROD");

    // DEV 사전 데이터를 PROD로 이동
    dictionaryDataDeploymentService.moveDictionaryDevToProd();

    // PROD 환경으로 전환
    environmentService.switchToProd();

    // DEV 환경 초기화
    environmentService.resetEnvironment(EnvironmentType.DEV);

    log.info("환경 전환 완료");
  }
}
