package com.yjlee.search.analysis.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.yjlee.search.analysis.dto.TempIndexRefreshResponse;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import com.yjlee.search.index.provider.IndexNameProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class TempIndexService {

  private static final String TEMP_SYNONYM_SET = "synonyms-temp-current";
  private static final String TEMP_USER_DICT_PATH =
      "/usr/share/elasticsearch/config/analysis/user/temp-current.txt";
  private static final String TEMP_STOPWORD_DICT_PATH =
      "/usr/share/elasticsearch/config/analysis/stopword/temp-current.txt";
  private static final String TEMP_UNIT_DICT_PATH =
      "/usr/share/elasticsearch/config/analysis/unit/temp-current.txt";

  private final ElasticsearchClient elasticsearchClient;
  private final ResourceLoader resourceLoader;
  private final DictionaryDataDeploymentService dictionaryDataDeploymentService;
  private final IndexNameProvider indexNameProvider;

  protected ResourceLoader getResourceLoader() {
    return resourceLoader;
  }

  public void refreshTempIndex() throws IOException {
    String tempIndexName = indexNameProvider.getTempAnalysisIndex();
    log.info("임시 인덱스 갱신 시작: {}", tempIndexName);

    // 1. 모든 사전을 임시 환경으로 배포
    deployTempDictionaries();
    log.info("모든 사전 임시 환경 배포 완료");

    // 2. 기존 인덱스 삭제 (있으면)
    if (indexExists(tempIndexName)) {
      deleteIndex(tempIndexName);
      log.info("기존 임시 인덱스 삭제 완료: {}", tempIndexName);
    }

    // 3. 인덱스 설정 및 매핑 준비
    String mappingJson = loadResourceFile("elasticsearch/product-mapping.json");
    String settingsJson = createTempIndexSettings();

    // 4. 새 인덱스 생성
    CreateIndexRequest request =
        CreateIndexRequest.of(
            i ->
                i.index(tempIndexName)
                    .mappings(
                        TypeMapping.of(
                            m -> m.withJson(new ByteArrayInputStream(mappingJson.getBytes()))))
                    .settings(
                        IndexSettings.of(
                            s -> s.withJson(new ByteArrayInputStream(settingsJson.getBytes())))));

    elasticsearchClient.indices().create(request);
    log.info("임시 인덱스 생성 완료: {}", tempIndexName);
  }

  private boolean indexExists(String indexName) throws IOException {
    ExistsRequest request = ExistsRequest.of(e -> e.index(indexName));
    return elasticsearchClient.indices().exists(request).value();
  }

  private void deleteIndex(String indexName) throws IOException {
    DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
    elasticsearchClient.indices().delete(request);
  }

  protected String createTempIndexSettings() throws IOException {
    String templateJson = loadResourceFile("elasticsearch/product-settings.json");

    // 임시 사전 파일 경로로 교체
    String modifiedJson =
        templateJson
            .replace("{USER_DICT_PATH}", TEMP_USER_DICT_PATH)
            .replace("{STOPWORD_DICT_PATH}", TEMP_STOPWORD_DICT_PATH)
            .replace("{UNIT_DICT_PATH}", TEMP_UNIT_DICT_PATH)
            .replace("{SYNONYM_SET_NAME}", TEMP_SYNONYM_SET);

    return modifiedJson;
  }

  protected String loadResourceFile(String path) throws IOException {
    return StreamUtils.copyToString(
        resourceLoader.getResource("classpath:" + path).getInputStream(), StandardCharsets.UTF_8);
  }

  private void deployTempDictionaries() {
    dictionaryDataDeploymentService.deployAllToTemp();
  }

  public String getTempIndexName() {
    return indexNameProvider.getTempAnalysisIndex();
  }

  public boolean isTempIndexExists() {
    try {
      return indexExists(indexNameProvider.getTempAnalysisIndex());
    } catch (IOException e) {
      log.error("임시 인덱스 존재 확인 실패", e);
      return false;
    }
  }

  public TempIndexRefreshResponse refreshTempIndexWithResponse() {
    log.info("임시 인덱스 갱신 요청");

    try {
      refreshTempIndex();
      log.info("임시 인덱스 갱신 완료");

      return TempIndexRefreshResponse.builder()
          .status("success")
          .message("임시 인덱스가 성공적으로 갱신되었습니다")
          .indexName(getTempIndexName())
          .build();
    } catch (IOException e) {
      throw new RuntimeException("임시 인덱스 갱신 중 오류가 발생했습니다: " + e.getMessage());
    }
  }
}
