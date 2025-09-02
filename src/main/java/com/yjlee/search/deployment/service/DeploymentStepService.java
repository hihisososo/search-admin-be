package com.yjlee.search.deployment.service;

import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeploymentStepService {

  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final DeploymentHistoryRepository deploymentHistoryRepository;
  private final ElasticsearchIndexService elasticsearchIndexService;

  public void updateAlias(String devIndexName) {
    log.info("Alias 업데이트 시작: products-search -> {}", devIndexName);

    logCurrentAliasState("배포 전");
    try {
      elasticsearchIndexService.updateProductsSearchAlias(devIndexName);

      // 자동완성 인덱스 alias도 함께 업데이트
      String autocompleteIndexName =
          elasticsearchIndexService.getAutocompleteIndexNameFromProductIndex(devIndexName);
      elasticsearchIndexService.updateAutocompleteSearchAlias(autocompleteIndexName);
      log.info("Alias 업데이트 완료: autocomplete-search -> {}", autocompleteIndexName);
    } catch (Exception e) {
      log.error("Alias 업데이트 실패", e);
      throw new RuntimeException("Alias 업데이트 실패: " + e.getMessage(), e);
    }
    logCurrentAliasState("배포 후");
  }

  public void deleteOldProdIndex(IndexEnvironment prodEnvironment) {
    // 상품 인덱스 삭제
    if (prodEnvironment.getIndexName() != null) {
      try {
        elasticsearchIndexService.deleteIndexIfExists(prodEnvironment.getIndexName());
        log.info("기존 운영 인덱스 삭제 완료: {}", prodEnvironment.getIndexName());
      } catch (Exception e) {
        log.warn("기존 운영 인덱스 삭제 실패 (무시하고 계속 진행): {}", e.getMessage());
      }
    }

    // 자동완성 인덱스 삭제
    if (prodEnvironment.getAutocompleteIndexName() != null) {
      try {
        elasticsearchIndexService.deleteIndexIfExists(prodEnvironment.getAutocompleteIndexName());
        log.info("기존 운영 자동완성 인덱스 삭제 완료: {}", prodEnvironment.getAutocompleteIndexName());
      } catch (Exception e) {
        log.warn("기존 운영 자동완성 인덱스 삭제 실패 (무시하고 계속 진행): {}", e.getMessage());
      }
    }
  }

  public void updateProdEnvironment(
      IndexEnvironment prodEnvironment, IndexEnvironment devEnvironment) {

    prodEnvironment.setIndexName(devEnvironment.getIndexName());
    prodEnvironment.setAutocompleteIndexName(devEnvironment.getAutocompleteIndexName());
    prodEnvironment.setVersion(devEnvironment.getVersion());
    prodEnvironment.setDocumentCount(devEnvironment.getDocumentCount());
    prodEnvironment.setIndexStatus(IndexEnvironment.IndexStatus.ACTIVE);
    prodEnvironment.setIndexDate(LocalDateTime.now());

    indexEnvironmentRepository.save(prodEnvironment);
    log.info("운영 환경 정보 업데이트 완료");
  }

  public void completeDeploymentHistory(Long historyId, Long documentCount) {
    DeploymentHistory history =
        deploymentHistoryRepository
            .findById(historyId)
            .orElseThrow(() -> new IllegalStateException("이력을 찾을 수 없습니다"));

    history.complete(LocalDateTime.now(), documentCount);
    deploymentHistoryRepository.save(history);
    log.info("배포 이력 완료 처리");
  }

  public void failDeploymentHistory(Long historyId) {
    DeploymentHistory history =
        deploymentHistoryRepository
            .findById(historyId)
            .orElseThrow(() -> new IllegalStateException("이력을 찾을 수 없습니다"));

    history.fail();
    deploymentHistoryRepository.save(history);
    log.error("배포 이력 실패 처리");
  }

  public void cleanupDevEnvironment(IndexEnvironment devEnvironment) {
    log.info("개발 환경 초기화 시작");

    devEnvironment.setIndexStatus(IndexEnvironment.IndexStatus.INACTIVE);
    devEnvironment.setVersion(null);
    devEnvironment.setIndexName("");
    devEnvironment.setAutocompleteIndexName("");
    devEnvironment.setDocumentCount(0L);
    devEnvironment.setIndexDate(null);
    devEnvironment.setIndexedDocumentCount(0L);
    devEnvironment.setTotalDocumentCount(0L);

    indexEnvironmentRepository.save(devEnvironment);

    log.info("개발 환경 초기화 완료");
  }

  private void logCurrentAliasState(String phase) {
    try {
      var aliasIndices = elasticsearchIndexService.getCurrentAliasIndices();
      log.info("{} products-search alias 상태: {}", phase, aliasIndices);
    } catch (Exception e) {
      log.warn("{} alias 상태 확인 실패: {}", phase, e.getMessage());
    }
  }
}
