package com.yjlee.search.deployment.service;

import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.synonym.service.SynonymDictionaryService;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
import com.yjlee.search.dictionary.user.service.UserDictionaryService;
import com.yjlee.search.index.service.ProductIndexingService;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingExecutionService {

  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final DeploymentHistoryRepository deploymentHistoryRepository;
  private final ProductIndexingService productIndexingService;
  private final SynonymDictionaryService synonymDictionaryService;
  private final UserDictionaryService userDictionaryService;
  private final UserDictionaryRepository userDictionaryRepository;
  private final EC2DeploymentService ec2DeploymentService;
  private final ElasticsearchIndexService elasticsearchIndexService;

  @Async("deploymentTaskExecutor")
  public void executeIndexingAsync(Long environmentId, String version, Long historyId) {
    try {
      log.info("비동기 색인 시작 - 버전: {}", version);

      // 1. 사전 스냅샷 생성
      createDictionarySnapshots();

      // 2. 사용자사전 EC2 업로드
      uploadUserDictionaryToEC2(version);

      // 3. 새 인덱스 생성 및 데이터 색인
      String newIndexName = elasticsearchIndexService.createNewIndex(version);
      int documentCount = indexProductsToNewIndex(newIndexName);

      // 4. 완료 처리
      completeIndexing(environmentId, historyId, newIndexName, version, documentCount);

      log.info("색인 완료 - 버전: {}, 문서 수: {}", version, documentCount);

    } catch (Exception e) {
      log.error("색인 실패 - 버전: {}", version, e);
      failIndexing(environmentId, historyId);
    }
  }

  @Transactional
  public void completeIndexing(
      Long environmentId, Long historyId, String newIndexName, String version, int documentCount) {
    // 기존 인덱스 삭제
    IndexEnvironment devEnvironment =
        indexEnvironmentRepository
            .findById(environmentId)
            .orElseThrow(() -> new IllegalStateException("환경을 찾을 수 없습니다"));

    deleteOldDevIndexSafely(devEnvironment.getIndexName());

    // 환경 업데이트
    devEnvironment.setIndexName(newIndexName);
    devEnvironment.completeIndexing(version, (long) documentCount);
    indexEnvironmentRepository.save(devEnvironment);

    // 이력 완료
    DeploymentHistory history =
        deploymentHistoryRepository
            .findById(historyId)
            .orElseThrow(() -> new IllegalStateException("이력을 찾을 수 없습니다"));
    history.complete(LocalDateTime.now(), (long) documentCount);
    deploymentHistoryRepository.save(history);
  }

  @Transactional
  public void failIndexing(Long environmentId, Long historyId) {
    // 환경 상태 복구
    IndexEnvironment environment =
        indexEnvironmentRepository
            .findById(environmentId)
            .orElseThrow(() -> new IllegalStateException("환경을 찾을 수 없습니다"));
    environment.failIndexing();
    indexEnvironmentRepository.save(environment);

    // 이력 실패 처리
    DeploymentHistory history =
        deploymentHistoryRepository
            .findById(historyId)
            .orElseThrow(() -> new IllegalStateException("이력을 찾을 수 없습니다"));
    history.fail();
    deploymentHistoryRepository.save(history);
  }

  private void createDictionarySnapshots() {
    try {
      synonymDictionaryService.createVersion();
      userDictionaryService.createVersion();
      log.info("사전 스냅샷 생성 완료");
    } catch (Exception e) {
      log.error("사전 스냅샷 생성 실패", e);
      throw new RuntimeException("사전 스냅샷 생성 실패", e);
    }
  }

  private void uploadUserDictionaryToEC2(String version) {
    try {
      String userDictContent = getCurrentUserDictionaryContent();

      EC2DeploymentService.EC2DeploymentResult result =
          ec2DeploymentService.deployUserDictionary(userDictContent, version);

      if (!result.isSuccess()) {
        throw new RuntimeException("사용자사전 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("사용자사전 EC2 업로드 완료 - 버전: {}, 내용 길이: {}", version, userDictContent.length());
    } catch (Exception e) {
      log.error("사용자사전 EC2 업로드 실패 - 버전: {}", version, e);
      throw new RuntimeException("사용자사전 EC2 업로드 실패", e);
    }
  }

  private String getCurrentUserDictionaryContent() {
    try {
      return userDictionaryRepository.findAll().stream()
          .map(dict -> dict.getKeyword())
          .reduce("", (acc, keyword) -> acc + keyword + "\n")
          .trim();
    } catch (Exception e) {
      log.error("사용자사전 내용 조회 실패", e);
      return "";
    }
  }

  private int indexProductsToNewIndex(String indexName) throws IOException {
    return productIndexingService.indexProductsToIndex(indexName);
  }

  private void deleteOldDevIndexSafely(String indexName) {
    if (indexName != null) {
      try {
        elasticsearchIndexService.deleteIndexIfExists(indexName);
        log.info("기존 개발 인덱스 삭제 완료: {}", indexName);
      } catch (Exception e) {
        log.warn("기존 개발 인덱스 삭제 실패 (무시하고 계속 진행): {}", e.getMessage());
      }
    }
  }
}
