package com.yjlee.search.deployment.service;

import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.AsyncTaskService;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.dto.*;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimpleDeploymentService {

  private final IndexEnvironmentRepository environmentRepository;
  private final DeploymentHistoryRepository historyRepository;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private final ElasticsearchSynonymService elasticsearchSynonymService;
  private final DictionaryDataDeploymentService dictionaryDeploymentService;
  private final AsyncTaskService asyncTaskService;
  private final AsyncIndexingService asyncIndexingService;
  private final Lock indexingLock = new ReentrantLock();

  /** 환경 목록 조회 */
  public EnvironmentListResponse getEnvironments() {
    List<IndexEnvironment> environments = environmentRepository.findAll();
    List<EnvironmentInfoResponse> responses =
        environments.stream().map(this::toEnvironmentResponse).toList();
    return EnvironmentListResponse.of(responses);
  }

  /** 색인 실행 (비동기) */
  @Transactional
  public Long executeIndexing(IndexingRequest request) {
    indexingLock.lock();
    try {
      log.debug("색인 시작: {}", request.getDescription());

      // 개발 환경 조회
      IndexEnvironment devEnv = getEnvironment(IndexEnvironment.EnvironmentType.DEV);

      // 색인 중복 실행 방지
      if (asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING)) {
        throw new IllegalStateException("색인이 이미 진행 중입니다.");
      }

      // 개발 환경 초기화 - 색인 시작 전 상태 리셋
      devEnv.reset();
      environmentRepository.save(devEnv);

      // 버전 생성 및 이력 저장
      String version = generateVersion();
      DeploymentHistory history =
          createHistory(
              DeploymentHistory.DeploymentType.INDEXING, version, request.getDescription());
      history = historyRepository.save(history);

      // AsyncTask 생성
      String initialMessage = String.format("색인 준비 중... (버전: %s)", version);
      var task = asyncTaskService.createTask(AsyncTaskType.INDEXING, initialMessage);

      // 비동기 색인 실행
      asyncIndexingService.executeIndexingAsync(
          devEnv.getId(), version, history.getId(), task.getId());

      return task.getId();
    } finally {
      indexingLock.unlock();
    }
  }

  /** 색인 실행 (비동기) - Response 포함 */
  @Transactional
  public IndexingStartResponse executeIndexingWithResponse(IndexingRequest request) {
    try {
      Long taskId = executeIndexing(request);
      return IndexingStartResponse.builder().taskId(taskId).message("색인 작업이 시작되었습니다").build();
    } catch (IllegalStateException e) {
      log.error("색인 실행 실패: {}", e.getMessage());
      return IndexingStartResponse.builder().taskId(null).message(e.getMessage()).build();
    }
  }

  /** 배포 실행 (DEV → PROD) */
  @Transactional
  public DeploymentOperationResponse executeDeployment(DeploymentRequest request) {
    log.debug("배포 시작: {}", request.getDescription());

    try {
      // 환경 조회
      IndexEnvironment devEnv = getEnvironment(IndexEnvironment.EnvironmentType.DEV);
      IndexEnvironment prodEnv = getEnvironment(IndexEnvironment.EnvironmentType.PROD);

      // 배포 가능 검증
      validateDeployment(devEnv, prodEnv);

      // 배포 이력 생성
      DeploymentHistory history =
          createHistory(
              DeploymentHistory.DeploymentType.DEPLOYMENT,
              devEnv.getVersion(),
              request.getDescription());
      history = historyRepository.save(history);

      // 배포 실행
      performDeployment(devEnv, prodEnv, history.getId());

      return DeploymentOperationResponse.success(
          "배포가 완료되었습니다.", devEnv.getVersion(), history.getId());

    } catch (Exception e) {
      log.error("배포 실패", e);
      return DeploymentOperationResponse.failure("배포 실패: " + e.getMessage());
    }
  }

  /** 배포 가능 검증 */
  private void validateDeployment(IndexEnvironment devEnv, IndexEnvironment prodEnv) {
    if (devEnv.getIndexStatus() != IndexEnvironment.IndexStatus.ACTIVE) {
      throw new IllegalStateException("개발 환경에 활성화된 색인이 없습니다.");
    }

    if (asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING)) {
      throw new IllegalStateException("개발 환경에서 색인이 진행 중입니다.");
    }
  }

  /** 실제 배포 수행 */
  private void performDeployment(
      IndexEnvironment devEnv, IndexEnvironment prodEnv, Long historyId) {
    try {
      log.info("배포 실행: {} → products-search", devEnv.getIndexName());

      // 1. 운영 사전 데이터 배포
      dictionaryDeploymentService.deployAllToProd();

      // 2. Alias 업데이트
      elasticsearchIndexService.updateProductsSearchAlias(devEnv.getIndexName());

      String autocompleteIndex =
          elasticsearchIndexService.getAutocompleteIndexNameFromProductIndex(devEnv.getIndexName());
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
      devEnv.reset();
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

  /** 배포 이력 조회 */
  public DeploymentHistoryListResponse getDeploymentHistory(
      Pageable pageable,
      DeploymentHistory.DeploymentStatus status,
      DeploymentHistory.DeploymentType type) {

    Page<DeploymentHistory> histories = historyRepository.findByFilters(status, type, pageable);
    Page<DeploymentHistoryResponse> responses = histories.map(DeploymentHistoryResponse::from);
    return DeploymentHistoryListResponse.of(responses);
  }

  /** 미사용 인덱스 조회 */
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

      // 전체 동의어 세트 조회
      List<String> allSynonymSets = elasticsearchSynonymService.getAllSynonymSets();

      // 사용 중인 동의어 세트 수집 (synonyms-nori-{version} 패턴)
      Set<String> usedSynonymSets = new HashSet<>();
      for (String indexName : usedIndices) {
        // products-v20240909092544 -> v20240909092544
        if (indexName.startsWith("products-v") || indexName.startsWith("autocomplete-v")) {
          String version = indexName.substring(indexName.indexOf("-v") + 1);
          String synonymSetName = "synonyms-nori-" + version;
          usedSynonymSets.add(synonymSetName);
        }
      }

      // 미사용 동의어 세트 찾기
      List<String> unusedSynonymSets =
          allSynonymSets.stream()
              .filter(set -> set.startsWith("synonyms-nori-"))
              .filter(set -> !usedSynonymSets.contains(set))
              .collect(Collectors.toList());

      return UnusedIndicesResponse.of(
          unusedIndices,
          new ArrayList<>(usedIndices),
          new ArrayList<>(aliasedIndices),
          unusedSynonymSets);
    } catch (Exception e) {
      log.error("미사용 인덱스 조회 실패", e);
      throw new RuntimeException("미사용 인덱스 조회 실패", e);
    }
  }

  /** 미사용 인덱스 삭제 - 확인 플래그 포함 */
  @Transactional
  public DeleteUnusedIndicesResponse deleteUnusedIndicesWithConfirmation(boolean confirmDelete) {
    if (!confirmDelete) {
      log.warn("미사용 인덱스 삭제 요청 거부 - confirmDelete=false");
      return DeleteUnusedIndicesResponse.of(
          new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), new ArrayList<>(), 0);
    }

    log.info("미사용 인덱스 삭제 요청 - confirmDelete=true");
    DeleteUnusedIndicesResponse response = deleteUnusedIndices();

    if (response.isSuccess()) {
      log.info("미사용 인덱스 삭제 완료 - {}개 삭제", response.getDeletedCount());
    } else {
      log.warn(
          "미사용 인덱스 삭제 부분 실패 - 성공: {}, 실패: {}",
          response.getDeletedCount(),
          response.getFailedCount());
    }

    return response;
  }

  /** 미사용 인덱스 삭제 */
  @Transactional
  public DeleteUnusedIndicesResponse deleteUnusedIndices() {
    try {
      UnusedIndicesResponse unused = getUnusedIndices();
      List<String> unusedIndices = unused.getUnusedIndices();
      List<String> unusedSynonymSets = unused.getUnusedSynonymSets();

      if (unusedIndices.isEmpty() && unusedSynonymSets.isEmpty()) {
        return DeleteUnusedIndicesResponse.of(
            new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>(), new ArrayList<>(), 0);
      }

      log.info("미사용 인덱스 삭제: {}개, 동의어 세트 삭제: {}개", unusedIndices.size(), unusedSynonymSets.size());

      // 인덱스 삭제
      List<String> deletedIndices = elasticsearchIndexService.deleteUnusedIndices(unusedIndices);
      List<String> failedIndices = new ArrayList<>(unusedIndices);
      failedIndices.removeAll(deletedIndices);

      // 동의어 세트 삭제 (인덱스 삭제 후 진행)
      List<String> deletedSynonymSets =
          elasticsearchSynonymService.deleteSynonymSets(unusedSynonymSets);
      List<String> failedSynonymSets = new ArrayList<>(unusedSynonymSets);
      failedSynonymSets.removeAll(deletedSynonymSets);

      // 삭제 이력 생성
      String description = String.format("미사용 인덱스 %d개 삭제", deletedIndices.size());
      if (!deletedSynonymSets.isEmpty()) {
        description += String.format(", 동의어 세트 %d개 삭제", deletedSynonymSets.size());
      }

      DeploymentHistory history =
          createHistory(DeploymentHistory.DeploymentType.CLEANUP, generateVersion(), description);
      history.setStatus(
          failedIndices.isEmpty() && failedSynonymSets.isEmpty()
              ? DeploymentHistory.DeploymentStatus.COMPLETED
              : DeploymentHistory.DeploymentStatus.PARTIAL);
      history.setDocumentCount((long) (deletedIndices.size() + deletedSynonymSets.size()));
      historyRepository.save(history);

      return DeleteUnusedIndicesResponse.of(
          deletedIndices,
          failedIndices,
          unusedIndices.size(),
          deletedSynonymSets,
          failedSynonymSets,
          unusedSynonymSets.size());

    } catch (Exception e) {
      log.error("미사용 인덱스 삭제 실패", e);
      throw new RuntimeException("미사용 인덱스 삭제 실패", e);
    }
  }

  // === Private Helper Methods ===

  private IndexEnvironment getEnvironment(IndexEnvironment.EnvironmentType type) {
    return environmentRepository
        .findByEnvironmentType(type)
        .orElseThrow(() -> new IllegalStateException("환경 설정이 없습니다: " + type));
  }

  private String generateVersion() {
    return "v" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }

  private DeploymentHistory createHistory(
      DeploymentHistory.DeploymentType type, String version, String description) {
    return DeploymentHistory.builder()
        .deploymentType(type)
        .status(DeploymentHistory.DeploymentStatus.IN_PROGRESS)
        .version(version)
        .description(description != null ? description : type.name())
        .build();
  }

  private void completeHistory(Long historyId, Long count) {
    DeploymentHistory history =
        historyRepository
            .findById(historyId)
            .orElseThrow(() -> new IllegalStateException("이력을 찾을 수 없습니다"));
    history.complete(LocalDateTime.now(), count);
    historyRepository.save(history);
  }

  private void failHistory(Long historyId) {
    DeploymentHistory history =
        historyRepository
            .findById(historyId)
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
        .build();
  }
}
