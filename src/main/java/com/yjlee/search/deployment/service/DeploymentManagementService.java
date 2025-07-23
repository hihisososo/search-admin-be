package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.dto.*;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.stopword.service.StopwordDictionaryService;
import com.yjlee.search.dictionary.synonym.service.SynonymDictionaryService;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import com.yjlee.search.dictionary.user.service.UserDictionaryService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeploymentManagementService {

  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final DeploymentHistoryRepository deploymentHistoryRepository;
  private final ElasticsearchClient elasticsearchClient;
  private final IndexingExecutionService indexingExecutionService;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private final SynonymDictionaryService synonymDictionaryService;
  private final UserDictionaryService userDictionaryService;
  private final StopwordDictionaryService stopwordDictionaryService;
  private final TypoCorrectionDictionaryService typoCorrectionDictionaryService;
  private final ElasticsearchSynonymService elasticsearchSynonymService;

  public EnvironmentListResponse getEnvironments() {
    List<IndexEnvironment> environments = indexEnvironmentRepository.findAll();

    // 실시간 문서 수 업데이트
    updateEnvironmentDocumentCounts(environments);

    List<EnvironmentInfoResponse> responses =
        environments.stream().map(EnvironmentInfoResponse::from).toList();

    return EnvironmentListResponse.of(responses);
  }

  private void updateEnvironmentDocumentCounts(List<IndexEnvironment> environments) {
    for (IndexEnvironment env : environments) {
      if (env.getIndexName() != null && !env.getIndexName().endsWith("-temp")) {
        try {
          Long currentCount = getDocumentCount(env.getIndexName());
          if (!currentCount.equals(env.getDocumentCount())) {
            env.setDocumentCount(currentCount);
            indexEnvironmentRepository.save(env);
          }
        } catch (Exception e) {
          log.warn("문서 수 업데이트 실패 - Index: {}", env.getIndexName(), e);
        }
      }
    }
  }

  private Long getDocumentCount(String indexName) throws IOException {
    try {
      if (!elasticsearchIndexService.indexExists(indexName)) {
        return 0L;
      }

      SearchRequest searchRequest =
          SearchRequest.of(s -> s.index(indexName).size(0).trackTotalHits(t -> t.enabled(true)));

      SearchResponse<Void> response = elasticsearchClient.search(searchRequest, Void.class);
      return response.hits().total().value();
    } catch (Exception e) {
      log.error("문서 수 조회 실패 - Index: {}", indexName, e);
      return 0L;
    }
  }

  @Transactional(readOnly = false)
  public synchronized DeploymentOperationResponse executeIndexing(IndexingRequest request) {
    log.info("색인 실행 시작 - 설명: {}", request.getDescription());

    try {
      // 1. 상태 변경을 별도 트랜잭션으로 즉시 커밋
      Long environmentId = updateIndexingStatus(request);
      if (environmentId == null) {
        return DeploymentOperationResponse.failure("현재 색인이 진행 중입니다.");
      }

      // 2. 버전 생성 및 이력 생성
      String version = generateVersion();
      Long historyId = createIndexingHistoryTransaction(version, request.getDescription());

      // 3. 비동기 실행 (모든 상태 변경 완료 후)
      indexingExecutionService.executeIndexingAsync(environmentId, version, historyId);

      return DeploymentOperationResponse.success("색인이 시작되었습니다.", version, historyId);

    } catch (Exception e) {
      log.error("색인 실행 실패", e);
      return DeploymentOperationResponse.failure("색인 실행 실패: " + e.getMessage());
    }
  }

  public Long updateIndexingStatus(IndexingRequest request) {
    IndexEnvironment devEnvironment = getOrCreateEnvironment(IndexEnvironment.EnvironmentType.DEV);

    if (devEnvironment.getId() == null) {
      devEnvironment = indexEnvironmentRepository.save(devEnvironment);
    }

    // 색인 중복 실행 방지
    IndexEnvironment latestDevEnvironment =
        indexEnvironmentRepository.findById(devEnvironment.getId()).orElse(devEnvironment);

    if (latestDevEnvironment.getIsIndexing()) {
      return null;
    }

    // 상태 변경 및 즉시 커밋
    latestDevEnvironment.startIndexing();
    IndexEnvironment savedEnvironment = indexEnvironmentRepository.save(latestDevEnvironment);

    log.info("색인 상태 변경 완료 - 환경 ID: {}", savedEnvironment.getId());
    return savedEnvironment.getId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long createIndexingHistoryTransaction(String version, String description) {
    DeploymentHistory history = createIndexingHistory(version, description);
    DeploymentHistory saved = deploymentHistoryRepository.save(history);
    return saved.getId();
  }

  @Transactional(readOnly = false)
  public DeploymentOperationResponse executeDeployment(DeploymentRequest request) {
    log.info("배포 실행 시작 - 설명: {}", request.getDescription());

    try {
      // 개발 환경 조회
      IndexEnvironment devEnvironment =
          getOrCreateEnvironment(IndexEnvironment.EnvironmentType.DEV);

      // 동적 생성된 환경이면 DB에 저장
      if (devEnvironment.getId() == null) {
        devEnvironment = indexEnvironmentRepository.save(devEnvironment);
      }

      if (devEnvironment.getIndexStatus() != IndexEnvironment.IndexStatus.ACTIVE) {
        return DeploymentOperationResponse.failure("개발 환경에 활성화된 색인이 없습니다.");
      }

      if (devEnvironment.getIsIndexing()) {
        return DeploymentOperationResponse.failure("개발 환경에서 색인이 진행 중입니다.");
      }

      // 운영 환경 조회
      IndexEnvironment prodEnvironment =
          getOrCreateEnvironment(IndexEnvironment.EnvironmentType.PROD);

      // 동적 생성된 환경이면 DB에 저장
      if (prodEnvironment.getId() == null) {
        prodEnvironment = indexEnvironmentRepository.save(prodEnvironment);
      }

      // 배포 이력 생성
      DeploymentHistory history =
          createDeploymentHistory(devEnvironment.getVersion(), request.getDescription());
      history = deploymentHistoryRepository.save(history);

      // 사전 운영 환경 배포
      deployDictionariesToProd();

      // 배포 실행
      executeDeploymentInternal(devEnvironment, prodEnvironment, history.getId());

      return DeploymentOperationResponse.success(
          "배포가 완료되었습니다.", devEnvironment.getVersion(), history.getId());

    } catch (Exception e) {
      log.error("배포 실행 실패", e);
      return DeploymentOperationResponse.failure("배포 실행 실패: " + e.getMessage());
    }
  }

  private void deployDictionariesToProd() {
    try {
      // 사전 스냅샷 운영 환경 배포
      synonymDictionaryService.deployToProd();
      userDictionaryService.deployToProd();
      stopwordDictionaryService.deployToProd();
      typoCorrectionDictionaryService.deployToProd();

      // 동의어 사전 PROD 환경 synonym_set 업데이트
      elasticsearchSynonymService.updateSynonymSetRealtime(DictionaryEnvironmentType.PROD);

      log.info("모든 사전 운영 환경 배포 및 synonym_set 업데이트 완료");
    } catch (Exception e) {
      log.error("사전 운영 환경 배포 실패", e);
      throw new RuntimeException("사전 운영 환경 배포 실패", e);
    }
  }

  public DeploymentHistoryListResponse getDeploymentHistory(
      Pageable pageable,
      DeploymentHistory.DeploymentStatus status,
      DeploymentHistory.DeploymentType deploymentType) {
    Page<DeploymentHistory> histories =
        deploymentHistoryRepository.findByFilters(status, deploymentType, pageable);
    Page<DeploymentHistoryResponse> responses = histories.map(DeploymentHistoryResponse::from);

    return DeploymentHistoryListResponse.of(responses);
  }

  @Transactional(readOnly = false)
  protected void executeDeploymentInternal(
      IndexEnvironment devEnvironment, IndexEnvironment prodEnvironment, Long historyId) {
    try {
      log.info("배포 실행 - 개발: {} -> 운영", devEnvironment.getIndexName());

      // 0. 배포 전 현재 alias 상태 확인
      try {
        var currentAliasIndices = elasticsearchIndexService.getCurrentAliasIndices();
        log.info("배포 전 products-search alias 상태: {}", currentAliasIndices);
      } catch (Exception e) {
        log.warn("배포 전 alias 상태 확인 실패 (무시하고 계속 진행): {}", e.getMessage());
      }

      // 1. products-search alias를 개발 인덱스로 변경
      elasticsearchIndexService.updateProductsSearchAlias(devEnvironment.getIndexName());

      // 1-1. 배포 후 alias 상태 확인
      try {
        var updatedAliasIndices = elasticsearchIndexService.getCurrentAliasIndices();
        log.info("배포 후 products-search alias 상태: {}", updatedAliasIndices);
      } catch (Exception e) {
        log.warn("배포 후 alias 상태 확인 실패 (무시하고 계속 진행): {}", e.getMessage());
      }

      // 2. 기존 운영 인덱스 삭제 (있으면 삭제)
      if (prodEnvironment.getIndexName() != null) {
        try {
          elasticsearchIndexService.deleteIndexIfExists(prodEnvironment.getIndexName());
          log.info("기존 운영 인덱스 삭제 완료: {}", prodEnvironment.getIndexName());
        } catch (Exception e) {
          log.warn("기존 운영 인덱스 삭제 실패 (무시하고 계속 진행): {}", e.getMessage());
        }
      }

      // 3. 운영 환경 정보 업데이트
      prodEnvironment.setIndexName(devEnvironment.getIndexName());
      prodEnvironment.setVersion(devEnvironment.getVersion());
      prodEnvironment.setDocumentCount(devEnvironment.getDocumentCount());
      prodEnvironment.setIndexStatus(IndexEnvironment.IndexStatus.ACTIVE);
      prodEnvironment.setIndexDate(LocalDateTime.now());
      indexEnvironmentRepository.save(prodEnvironment);

      // 4. 이력 완료 처리
      LocalDateTime completionTime = LocalDateTime.now();
      DeploymentHistory history =
          deploymentHistoryRepository
              .findById(historyId)
              .orElseThrow(() -> new IllegalStateException("이력을 찾을 수 없습니다"));
      history.complete(completionTime, devEnvironment.getDocumentCount());
      deploymentHistoryRepository.save(history);

      log.info("배포 완료 - 운영 인덱스: {}", prodEnvironment.getIndexName());

    } catch (Exception e) {
      log.error("배포 실패", e);
      DeploymentHistory history =
          deploymentHistoryRepository
              .findById(historyId)
              .orElseThrow(() -> new IllegalStateException("이력을 찾을 수 없습니다"));
      history.fail();
      deploymentHistoryRepository.save(history);
      throw new RuntimeException("배포 실패: " + e.getMessage(), e);
    }
  }

  private IndexEnvironment getOrCreateEnvironment(IndexEnvironment.EnvironmentType type) {
    return indexEnvironmentRepository
        .findByEnvironmentType(type)
        .orElseThrow(() -> new IllegalStateException("환경 설정이 없습니다: " + type));
  }

  private String generateVersion() {
    return "products-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }

  private DeploymentHistory createIndexingHistory(String version, String description) {
    return DeploymentHistory.builder()
        .deploymentType(DeploymentHistory.DeploymentType.INDEXING)
        .status(DeploymentHistory.DeploymentStatus.IN_PROGRESS)
        .version(version)
        .description(description != null ? description : "색인 작업")
        .build();
  }

  private DeploymentHistory createDeploymentHistory(String version, String description) {
    return DeploymentHistory.builder()
        .deploymentType(DeploymentHistory.DeploymentType.DEPLOYMENT)
        .status(DeploymentHistory.DeploymentStatus.IN_PROGRESS)
        .version(version)
        .description(description != null ? description : "배포 작업")
        .build();
  }
}
