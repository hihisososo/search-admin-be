package com.yjlee.search.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.AsyncTaskService;
import com.yjlee.search.async.service.TaskWorker;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.domain.IndexingContext;
import com.yjlee.search.deployment.domain.IndexingResult;
import com.yjlee.search.deployment.enums.DeploymentType;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.util.VersionGenerator;
import com.yjlee.search.dictionary.common.model.DictionaryData;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import com.yjlee.search.dictionary.common.service.DictionaryDataLoader;
import com.yjlee.search.index.provider.IndexNameProvider;
import com.yjlee.search.index.service.ProductIndexingService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexingTaskWorker implements TaskWorker {

  private static final int PROGRESS_INIT = 10;
  private static final int PROGRESS_INDEXING_START = 30;
  private static final int PROGRESS_INDEXING_END = 90;
  private static final int PROGRESS_COMPLETE = 95;

  private final IndexEnvironmentService environmentService;
  private final DeploymentHistoryService historyService;
  private final ProductIndexingService productIndexingService;
  private final DictionaryDataDeploymentService dictionaryDeploymentService;
  private final DictionaryDataLoader dictionaryDataLoader;
  private final ElasticsearchIndexService elasticsearchIndexService;
  private final ElasticsearchSettingsService settingsService;
  private final ElasticsearchMappingService mappingService;
  private final AsyncTaskService asyncTaskService;
  private final IndexNameProvider indexNameProvider;
  private final ObjectMapper objectMapper;

  @Override
  public AsyncTaskType getSupportedTaskType() {
    return AsyncTaskType.INDEXING;
  }

  @Override
  public void execute(AsyncTask task) {
    IndexingContext context = null;

    try {
      if (task.getParams() == null || task.getParams().isEmpty()) {
        throw new IllegalArgumentException("작업 파라미터가 없습니다");
      }

      Map<String, Object> params = objectMapper.readValue(task.getParams(), Map.class);
      String description = (String) params.get("description");

      // 환경 초기화 및 Context 생성
      context = prepareIndexingAndCreateContext(description);

      log.info("인덱싱 작업 시작: taskId={}, version={}", task.getId(), context.getVersion());

      // 사전 데이터 메모리 로드
      asyncTaskService.updateProgress(task.getId(), PROGRESS_INIT, "사전 데이터 로드 중...");
      DictionaryData dictionaryData =
          dictionaryDataLoader.loadAll(EnvironmentType.CURRENT, context.getVersion());

      // context에 preloaded 데이터 저장
      context.setPreloadedDictionaryData(dictionaryData);

      // 사전 데이터 DEV 환경으로 배포
      dictionaryDeploymentService.deployToEnvironment(dictionaryData, EnvironmentType.DEV);

      // 사전 데이터 업로드
      dictionaryDeploymentService.uploadDictionaries(dictionaryData);

      // 인덱스 생성
      createIndexes(context);

      // 상품 색인
      asyncTaskService.updateProgress(task.getId(), PROGRESS_INDEXING_START, "상품 색인 시작...");
      productIndexingService.setProgressCallback(
          (indexed, total) -> {
            int range = PROGRESS_INDEXING_END - PROGRESS_INDEXING_START;
            int progress = PROGRESS_INDEXING_START + (int) ((indexed * range) / total);
            String message = String.format("상품 색인 중: %d/%d", indexed, total);
            asyncTaskService.updateProgress(task.getId(), progress, message);
          });
      int documentCount = productIndexingService.indexProducts(context.getVersion());
      context.setDocumentCount(documentCount);

      // 색인 완료 처리
      asyncTaskService.updateProgress(task.getId(), PROGRESS_COMPLETE, "색인 완료 처리 중...");
      finalizeIndexing(context);

      asyncTaskService.completeTask(task.getId(), IndexingResult.from(context));

      log.info(
          "인덱싱 작업 완료: taskId={}, version={}, documentCount={}",
          task.getId(),
          context.getVersion(),
          context.getDocumentCount());

    } catch (Exception e) {
      log.error("인덱싱 작업 실패: taskId={}", task.getId(), e);
      asyncTaskService.failTask(task.getId(), "색인 실패: " + e.getMessage());

      if (context != null && context.getHistoryId() != null) {
        failHistorySilently(context.getHistoryId());
      }
    }
  }

  private void createIndexes(IndexingContext context) {
    String version = context.getVersion();
    String productIndexName = context.getProductIndexName();
    String autocompleteIndexName = context.getAutocompleteIndexName();
    String synonymSetName = context.getSynonymSetName();

    // 상품 인덱스 생성
    elasticsearchIndexService.deleteIndexIfExists(productIndexName);
    String productMapping = mappingService.loadProductMapping();
    String productSettings =
        settingsService.createProductIndexSettings(
            indexNameProvider.getUserDictPath(version),
            indexNameProvider.getStopwordDictPath(version),
            indexNameProvider.getUnitDictPath(version),
            synonymSetName);
    elasticsearchIndexService.createIndex(productIndexName, productMapping, productSettings);

    // 자동완성 인덱스 생성
    elasticsearchIndexService.deleteIndexIfExists(autocompleteIndexName);
    String autocompleteMapping = mappingService.loadAutocompleteMapping();
    String autocompleteSettings =
        settingsService.createAutocompleteIndexSettings(indexNameProvider.getUserDictPath(version));
    elasticsearchIndexService.createIndex(
        autocompleteIndexName, autocompleteMapping, autocompleteSettings);
  }

  public IndexingContext prepareIndexingAndCreateContext(String description) {
    String version = VersionGenerator.generateVersion();

    // 1. 히스토리 생성 (색인 시작 기록)
    DeploymentHistory history =
        historyService.createHistory(DeploymentType.INDEXING, version, description);

    // 2. 환경 리셋 및 준비 (DB 업데이트)
    environmentService.resetEnvironment(EnvironmentType.DEV);

    // 3. IndexContext 생성
    IndexingContext context =
        IndexingContext.create(history.getId(), description, version, indexNameProvider);

    // 4. 환경에 색인 준비 정보 업데이트 (DB 업데이트)
    environmentService.updatePrepareIndexing(
        EnvironmentType.DEV,
        context.getProductIndexName(),
        context.getAutocompleteIndexName(),
        context.getSynonymSetName(),
        version);

    // 5. 기존 인덱스 삭제 (Elasticsearch 작업)
    elasticsearchIndexService.deleteIndexIfExists(context.getProductIndexName());
    elasticsearchIndexService.deleteIndexIfExists(context.getAutocompleteIndexName());

    return context;
  }

  public void finalizeIndexing(IndexingContext context) {

    // 1. 사전 데이터 동기화 (preloaded 데이터 사용)
    dictionaryDeploymentService.sync(
        context.getPreloadedDictionaryData(), context.getSynonymSetName(), context.getVersion());

    // 2. 히스토리 업데이트
    historyService.updateHistoryStatus(
        context.getHistoryId(), true, (long) context.getDocumentCount());

    // 3. 환경 활성화 (IndexContext의 모든 정보 전달)
    environmentService.activateIndex(
        EnvironmentType.DEV,
        context.getProductIndexName(),
        context.getAutocompleteIndexName(),
        context.getSynonymSetName(),
        context.getVersion(),
        (long) context.getDocumentCount());
  }

  private void failHistorySilently(Long historyId) {
    historyService.updateHistoryStatus(historyId, false, null);
  }
}
