package com.yjlee.search.search.analysis.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.constant.DeploymentConstants;
import com.yjlee.search.deployment.service.EC2DeploymentService;
import com.yjlee.search.deployment.service.ElasticsearchSynonymService;
import com.yjlee.search.dictionary.stopword.service.StopwordDictionaryService;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import com.yjlee.search.dictionary.unit.repository.UnitDictionaryRepository;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class TempIndexService {

  private static final String TEMP_INDEX_NAME = "temp-analysis-current";
  private static final String TEMP_SYNONYM_SET = "synonyms-temp-current";
  private static final String TEMP_USER_DICT_PATH =
      "/usr/share/elasticsearch/config/analysis/user/temp-current.txt";
  private static final String TEMP_STOPWORD_DICT_PATH =
      "/usr/share/elasticsearch/config/analysis/stopword/temp-current.txt";
  private static final String TEMP_UNIT_DICT_PATH =
      "/usr/share/elasticsearch/config/analysis/unit/temp-current.txt";

  private final ElasticsearchClient elasticsearchClient;
  private final ElasticsearchSynonymService elasticsearchSynonymService;
  private final UserDictionaryRepository userDictionaryRepository;
  private final UnitDictionaryRepository unitDictionaryRepository;
  private final StopwordDictionaryService stopwordDictionaryService;
  private final EC2DeploymentService ec2DeploymentService;
  private final ResourceLoader resourceLoader;

  public void refreshTempIndex() throws IOException {
    log.info("임시 인덱스 갱신 시작: {}", TEMP_INDEX_NAME);

    // 1. 사용자 사전 EC2 업로드
    uploadTempUserDictionary();
    log.info("임시 사용자 사전 업로드 완료");

    // 2. 불용어 사전 EC2 업로드
    uploadTempStopwordDictionary();
    log.info("임시 불용어 사전 업로드 완료");

    // 3. 단위 사전 EC2 업로드
    uploadTempUnitDictionary();
    log.info("임시 단위 사전 업로드 완료");

    // 4. 기존 인덱스 삭제 (있으면)
    if (indexExists(TEMP_INDEX_NAME)) {
      deleteIndex(TEMP_INDEX_NAME);
      log.info("기존 임시 인덱스 삭제 완료: {}", TEMP_INDEX_NAME);
    }

    // 5. CURRENT 환경의 동의어 사전 생성/업데이트
    elasticsearchSynonymService.createOrUpdateSynonymSet(
        TEMP_SYNONYM_SET, DictionaryEnvironmentType.CURRENT);
    log.info("임시 동의어 세트 생성/업데이트 완료: {}", TEMP_SYNONYM_SET);

    // 6. 인덱스 설정 및 매핑 준비
    String mappingJson = loadResourceFile("elasticsearch/product-mapping.json");
    String settingsJson = createTempIndexSettings();

    // 7. 새 인덱스 생성
    CreateIndexRequest request =
        CreateIndexRequest.of(
            i ->
                i.index(TEMP_INDEX_NAME)
                    .mappings(
                        TypeMapping.of(
                            m -> m.withJson(new ByteArrayInputStream(mappingJson.getBytes()))))
                    .settings(
                        IndexSettings.of(
                            s -> s.withJson(new ByteArrayInputStream(settingsJson.getBytes())))));

    elasticsearchClient.indices().create(request);
    log.info("임시 인덱스 생성 완료: {}", TEMP_INDEX_NAME);
  }

  private boolean indexExists(String indexName) throws IOException {
    ExistsRequest request = ExistsRequest.of(e -> e.index(indexName));
    return elasticsearchClient.indices().exists(request).value();
  }

  private void deleteIndex(String indexName) throws IOException {
    DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
    elasticsearchClient.indices().delete(request);
  }

  private void uploadTempUserDictionary() {
    String content = buildUserDictionaryContent();
    EC2DeploymentService.EC2DeploymentResult result =
        ec2DeploymentService.deployFile(
            "temp-current.txt", DeploymentConstants.EC2Paths.USER_DICT, content, "temp-current");

    if (!result.isSuccess()) {
      throw new RuntimeException("임시 사용자 사전 EC2 업로드 실패: " + result.getMessage());
    }
  }

  private void uploadTempStopwordDictionary() {
    String content = buildStopwordDictionaryContent();
    EC2DeploymentService.EC2DeploymentResult result =
        ec2DeploymentService.deployFile(
            "temp-current.txt",
            DeploymentConstants.EC2Paths.STOPWORD_DICT,
            content,
            "temp-current");

    if (!result.isSuccess()) {
      throw new RuntimeException("임시 불용어 사전 EC2 업로드 실패: " + result.getMessage());
    }
  }

  private void uploadTempUnitDictionary() {
    String content = buildUnitDictionaryContent();
    EC2DeploymentService.EC2DeploymentResult result =
        ec2DeploymentService.deployFile(
            "temp-current.txt", DeploymentConstants.EC2Paths.UNIT_DICT, content, "temp-current");

    if (!result.isSuccess()) {
      throw new RuntimeException("임시 단위 사전 EC2 업로드 실패: " + result.getMessage());
    }
  }

  private String buildUserDictionaryContent() {
    List<UserDictionary> dictionaries = userDictionaryRepository.findAll();

    // Nori 사용자 사전 형식: 단어만 한 줄에 하나씩
    // 추가 정보가 필요한 경우 확장 가능
    return dictionaries.stream().map(UserDictionary::getKeyword).collect(Collectors.joining("\n"));
  }

  private String buildStopwordDictionaryContent() {
    // CURRENT 환경의 불용어 사전 가져오기 (페이징 없이 전체 조회)
    try {
      // 큰 페이지 크기로 전체 불용어 가져오기
      var response =
          stopwordDictionaryService.getList(
              0, 10000, "keyword", "asc", null, DictionaryEnvironmentType.CURRENT);

      return response.getContent().stream()
          .map(
              stopword -> {
                String keyword = stopword.getKeyword();
                // 쉼표로 구분된 불용어들을 줄바꿈으로 구분된 형태로 변환
                if (keyword.contains(",")) {
                  return String.join("\n", keyword.split(",")).replaceAll("\\s+", "");
                }
                return keyword;
              })
          .reduce("", (acc, keyword) -> acc + keyword + "\n")
          .trim();
    } catch (Exception e) {
      log.error("불용어 사전 내용 조회 실패", e);
      return "";
    }
  }

  private String buildUnitDictionaryContent() {
    List<UnitDictionary> dictionaries = unitDictionaryRepository.findAll();

    // 단위 사전 형식: kg,킬로그램
    return dictionaries.stream().map(UnitDictionary::getKeyword).collect(Collectors.joining("\n"));
  }

  private String createTempIndexSettings() throws IOException {
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

  private String loadResourceFile(String path) throws IOException {
    return StreamUtils.copyToString(
        resourceLoader.getResource("classpath:" + path).getInputStream(), StandardCharsets.UTF_8);
  }

  public String getTempIndexName() {
    return TEMP_INDEX_NAME;
  }

  public boolean isTempIndexExists() {
    try {
      return indexExists(TEMP_INDEX_NAME);
    } catch (IOException e) {
      log.error("임시 인덱스 존재 확인 실패", e);
      return false;
    }
  }
}
