package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.dto.*;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import com.yjlee.search.index.service.ProductIndexingService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimpleDeploymentService {

  @Value("${indexing.max-documents:}")
  private Integer maxDocumentsConfig;

  private final IndexEnvironmentRepository environmentRepository;
  private final DeploymentHistoryRepository historyRepository;
  private final ProductIndexingService productIndexingService;
  private final DictionaryDataDeploymentService dictionaryDeploymentService;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private final ElasticsearchClient elasticsearchClient;

  /**
   * 환경 목록 조회
   */
  public EnvironmentListResponse getEnvironments() {
    List<IndexEnvironment> environments = environmentRepository.findAll();
    List<EnvironmentInfoResponse> responses = environments.stream()
        .map(this::toEnvironmentResponse)
        .toList();
    return EnvironmentListResponse.of(responses);
  }

  /**
   * 색인 실행
   */
  @Transactional
  public synchronized DeploymentOperationResponse executeIndexing(IndexingRequest request) {
    log.info("색인 시작: {}", request.getDescription());

    try {
      // 개발 환경 조회
      IndexEnvironment devEnv = getEnvironment(IndexEnvironment.EnvironmentType.DEV);
      
      // 색인 중복 실행 방지
      if (devEnv.getIsIndexing()) {
        return DeploymentOperationResponse.failure("색인이 이미 진행 중입니다.");
      }

      // 색인 시작 상태 변경
      devEnv.startIndexing();
      environmentRepository.save(devEnv);

      // 버전 생성 및 이력 저장
      String version = generateVersion();
      DeploymentHistory history = createHistory(
          DeploymentHistory.DeploymentType.INDEXING, 
          version, 
          request.getDescription()
      );
      history = historyRepository.save(history);

      // 비동기 색인 실행
      executeIndexingAsync(devEnv.getId(), version, history.getId());

      return DeploymentOperationResponse.success("색인이 시작되었습니다.", version, history.getId());

    } catch (Exception e) {
      log.error("색인 실패", e);
      return DeploymentOperationResponse.failure("색인 실패: " + e.getMessage());
    }
  }

  /**
   * 비동기 색인 실행
   */
  @Async("deploymentTaskExecutor")
  public void executeIndexingAsync(Long envId, String version, Long historyId) {
    try {
      log.info("비동기 색인 시작 - 버전: {}", version);

      // 1. 사전 데이터 배포
      deployDictionaries(version);

      // 2. 새 인덱스 생성
      String newIndexName = elasticsearchIndexService.createNewIndex(
          version, 
          DictionaryEnvironmentType.DEV
      );

      // 3. 상품 색인
      productIndexingService.setProgressCallback((indexed, total) -> 
          updateIndexingProgress(envId, indexed, total)
      );
      
      int documentCount = indexProducts(newIndexName);

      // 4. 완료 처리
      completeIndexing(envId, historyId, newIndexName, version, documentCount);

      log.info("색인 완료 - 버전: {}, 문서: {}개", version, documentCount);

    } catch (Exception e) {
      log.error("색인 실패 - 버전: {}", version, e);
      failIndexing(envId, historyId);
    }
  }

  /**
   * 배포 실행 (DEV → PROD)
   */
  @Transactional
  public DeploymentOperationResponse executeDeployment(DeploymentRequest request) {
    log.info("배포 시작: {}", request.getDescription());

    try {
      // 환경 조회
      IndexEnvironment devEnv = getEnvironment(IndexEnvironment.EnvironmentType.DEV);
      IndexEnvironment prodEnv = getEnvironment(IndexEnvironment.EnvironmentType.PROD);

      // 배포 가능 검증
      validateDeployment(devEnv, prodEnv);

      // 배포 이력 생성
      DeploymentHistory history = createHistory(
          DeploymentHistory.DeploymentType.DEPLOYMENT,
          devEnv.getVersion(),
          request.getDescription()
      );
      history = historyRepository.save(history);

      // 배포 실행
      performDeployment(devEnv, prodEnv, history.getId());

      return DeploymentOperationResponse.success(
          "배포가 완료되었습니다.", 
          devEnv.getVersion(), 
          history.getId()
      );

    } catch (Exception e) {
      log.error("배포 실패", e);
      return DeploymentOperationResponse.failure("배포 실패: " + e.getMessage());
    }
  }

  /**
   * 배포 가능 검증
   */
  private void validateDeployment(IndexEnvironment devEnv, IndexEnvironment prodEnv) {
    if (devEnv.getIndexStatus() != IndexEnvironment.IndexStatus.ACTIVE) {
      throw new IllegalStateException("개발 환경에 활성화된 색인이 없습니다.");
    }
    
    if (devEnv.getIsIndexing()) {
      throw new IllegalStateException("개발 환경에서 색인이 진행 중입니다.");
    }
  }

  /**
   * 실제 배포 수행
   */
  private void performDeployment(IndexEnvironment devEnv, IndexEnvironment prodEnv, Long historyId) {
    try {
      log.info("배포 실행: {} → products-search", devEnv.getIndexName());

      // 1. 운영 사전 데이터 배포
      dictionaryDeploymentService.deployAllToProd();

      // 2. Alias 업데이트
      elasticsearchIndexService.updateProductsSearchAlias(devEnv.getIndexName());
      
      String autocompleteIndex = elasticsearchIndexService
          .getAutocompleteIndexNameFromProductIndex(devEnv.getIndexName());
      elasticsearchIndexService.updateAutocompleteSearchAlias(autocompleteIndex);

      // 3. 기존 운영 인덱스 삭제 (옵션)
      deleteOldIndex(prodEnv.getIndexName());
      deleteOldIndex(prodEnv.getAutocompleteIndexName());

      // 4. 운영 환경 정보 업데이트
      prodEnv.setIndexName(devEnv.getIndexName());
      prodEnv.setAutocompleteIndexName(devEnv.getAutocompleteIndexName());
      prodEnv.setVersion(devEnv.getVersion());
      prodEnv.setDocumentCount(devEnv.getDocumentCount());
      prodEnv.setIndexStatus(IndexEnvironment.IndexStatus.ACTIVE);
      prodEnv.setIndexDate(LocalDateTime.now());
      environmentRepository.save(prodEnv);

      // 5. 개발 환경 초기화
      devEnv.setIndexStatus(IndexEnvironment.IndexStatus.INACTIVE);
      devEnv.setIsIndexing(false);
      environmentRepository.save(devEnv);

      // 6. 개발 사전 데이터 초기화
      dictionaryDeploymentService.deleteAllByEnvironment(DictionaryEnvironmentType.DEV);

      // 7. 배포 이력 완료
      completeHistory(historyId, devEnv.getDocumentCount());

      // 8. 실시간 동기화
      dictionaryDeploymentService.realtimeSyncAll(DictionaryEnvironmentType.PROD);

      log.info("배포 완료: {}", prodEnv.getIndexName());

    } catch (Exception e) {
      failHistory(historyId);
      throw new RuntimeException("배포 실패", e);
    }
  }

  /**
   * 배포 이력 조회
   */
  public DeploymentHistoryListResponse getDeploymentHistory(
      Pageable pageable,
      DeploymentHistory.DeploymentStatus status,
      DeploymentHistory.DeploymentType type) {
    
    Page<DeploymentHistory> histories = historyRepository.findByFilters(status, type, pageable);
    Page<DeploymentHistoryResponse> responses = histories.map(DeploymentHistoryResponse::from);
    return DeploymentHistoryListResponse.of(responses);
  }

  /**
   * 미사용 인덱스 조회
   */
  public UnusedIndicesResponse getUnusedIndices() {
    try {
      // 사용 중인 인덱스 수집
      Set<String> usedIndices = new HashSet<>();
      for (IndexEnvironment env : environmentRepository.findAll()) {
        if (env.getIndexName() != null) usedIndices.add(env.getIndexName());
        if (env.getAutocompleteIndexName() != null) usedIndices.add(env.getAutocompleteIndexName());
      }

      // Alias 인덱스 수집
      Set<String> aliasedIndices = new HashSet<>();
      aliasedIndices.addAll(elasticsearchIndexService.getCurrentAliasIndices());
      aliasedIndices.addAll(elasticsearchIndexService.getCurrentAutocompleteAliasIndices());

      // 미사용 인덱스 찾기
      List<String> unusedIndices = elasticsearchIndexService.getUnusedIndices(usedIndices);

      return UnusedIndicesResponse.of(
          unusedIndices, 
          new ArrayList<>(usedIndices), 
          new ArrayList<>(aliasedIndices)
      );
    } catch (Exception e) {
      log.error("미사용 인덱스 조회 실패", e);
      throw new RuntimeException("미사용 인덱스 조회 실패", e);
    }
  }

  /**
   * 미사용 인덱스 삭제
   */
  @Transactional
  public DeleteUnusedIndicesResponse deleteUnusedIndices() {
    try {
      UnusedIndicesResponse unused = getUnusedIndices();
      List<String> unusedIndices = unused.getUnusedIndices();

      if (unusedIndices.isEmpty()) {
        return DeleteUnusedIndicesResponse.of(new ArrayList<>(), new ArrayList<>(), 0);
      }

      log.info("미사용 인덱스 삭제: {}개", unusedIndices.size());

      List<String> deletedIndices = elasticsearchIndexService.deleteUnusedIndices(unusedIndices);
      List<String> failedIndices = new ArrayList<>(unusedIndices);
      failedIndices.removeAll(deletedIndices);

      // 삭제 이력 생성
      DeploymentHistory history = createHistory(
          DeploymentHistory.DeploymentType.CLEANUP,
          generateVersion(),
          String.format("미사용 인덱스 정리 - %d개 삭제", deletedIndices.size())
      );
      history.setStatus(failedIndices.isEmpty() 
          ? DeploymentHistory.DeploymentStatus.COMPLETED 
          : DeploymentHistory.DeploymentStatus.PARTIAL);
      history.setDocumentCount((long) deletedIndices.size());
      historyRepository.save(history);

      return DeleteUnusedIndicesResponse.of(deletedIndices, failedIndices, unusedIndices.size());

    } catch (Exception e) {
      log.error("미사용 인덱스 삭제 실패", e);
      throw new RuntimeException("미사용 인덱스 삭제 실패", e);
    }
  }

  // === Private Helper Methods ===

  private IndexEnvironment getEnvironment(IndexEnvironment.EnvironmentType type) {
    return environmentRepository.findByEnvironmentType(type)
        .orElseThrow(() -> new IllegalStateException("환경 설정이 없습니다: " + type));
  }

  private String generateVersion() {
    return "v" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }

  private DeploymentHistory createHistory(
      DeploymentHistory.DeploymentType type, 
      String version, 
      String description) {
    return DeploymentHistory.builder()
        .deploymentType(type)
        .status(DeploymentHistory.DeploymentStatus.IN_PROGRESS)
        .version(version)
        .description(description != null ? description : type.name())
        .build();
  }

  private void deployDictionaries(String version) {
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

  @Transactional
  private void updateIndexingProgress(Long envId, Long indexed, Long total) {
    environmentRepository.findById(envId).ifPresent(env -> {
      env.updateIndexingProgress(indexed, total);
      environmentRepository.save(env);
    });
  }

  @Transactional
  private void completeIndexing(Long envId, Long historyId, String indexName, String version, int count) {
    // 환경 업데이트
    IndexEnvironment env = environmentRepository.findById(envId)
        .orElseThrow(() -> new IllegalStateException("환경을 찾을 수 없습니다"));
    
    // 기존 인덱스 삭제 (옵션)
    deleteOldIndex(env.getIndexName());
    deleteOldIndex(env.getAutocompleteIndexName());
    
    env.setIndexName(indexName);
    env.setAutocompleteIndexName(elasticsearchIndexService
        .getAutocompleteIndexNameFromProductIndex(indexName));
    env.completeIndexing(version, (long) count);
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

  @Transactional
  private void failIndexing(Long envId, Long historyId) {
    // 환경 복구
    IndexEnvironment env = environmentRepository.findById(envId)
        .orElseThrow(() -> new IllegalStateException("환경을 찾을 수 없습니다"));
    env.failIndexing();
    environmentRepository.save(env);

    // 이력 실패
    failHistory(historyId);
  }

  private void completeHistory(Long historyId, Long count) {
    DeploymentHistory history = historyRepository.findById(historyId)
        .orElseThrow(() -> new IllegalStateException("이력을 찾을 수 없습니다"));
    history.complete(LocalDateTime.now(), count);
    historyRepository.save(history);
  }

  private void failHistory(Long historyId) {
    DeploymentHistory history = historyRepository.findById(historyId)
        .orElseThrow(() -> new IllegalStateException("이력을 찾을 수 없습니다"));
    history.fail();
    historyRepository.save(history);
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

  private EnvironmentInfoResponse toEnvironmentResponse(IndexEnvironment env) {
    return EnvironmentInfoResponse.builder()
        .environmentType(env.getEnvironmentType().name())
        .environmentDescription(env.getEnvironmentType().getDescription())
        .indexName(env.getIndexName())
        .autocompleteIndexName(env.getAutocompleteIndexName())
        .documentCount(env.getDocumentCount())
        .indexStatus(env.getIndexStatus().name())
        .indexStatusDescription(env.getIndexStatus().getDescription())
        .indexDate(env.getIndexDate())
        .version(env.getVersion())
        .isIndexing(env.getIsIndexing())
        .indexingProgress(env.getIndexingProgress())
        .indexedDocumentCount(env.getIndexedDocumentCount())
        .totalDocumentCount(env.getTotalDocumentCount())
        .build();
  }
}