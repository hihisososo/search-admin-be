package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.synonyms.PutSynonymRequest;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.synonym.service.SynonymDictionaryService;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSynonymService {

  private final ElasticsearchClient elasticsearchClient;
  private final SynonymDictionaryService synonymDictionaryService;

  private static final String SYNONYM_SET_BASE_NAME = "synonyms-nori";

  /** 환경별 synonym set 이름 생성 */
  private String getSynonymSetName(DictionaryEnvironmentType environmentType) {
    switch (environmentType) {
      case CURRENT:
      case DEV:
        return SYNONYM_SET_BASE_NAME + "-dev";
      case PROD:
        return SYNONYM_SET_BASE_NAME + "-prod";
      default:
        return SYNONYM_SET_BASE_NAME + "-dev";
    }
  }

  /**
   * 실시간 동의어 사전 업데이트
   *
   * @param environmentType 환경 (DEV/PROD)
   */
  public void updateSynonymSetRealtime(DictionaryEnvironmentType environmentType) {
    String synonymSetName = getSynonymSetName(environmentType);
    log.info(
        "동의어 사전 실시간 업데이트 시작 - 환경: {}, synonym_set: {}",
        environmentType.getDescription(),
        synonymSetName);

    try {
      List<String> synonymRules = getSynonymRules(environmentType);

      if (synonymRules.isEmpty()) {
        log.warn("업데이트할 동의어 규칙이 없습니다 - 환경: {}", environmentType.getDescription());
        return;
      }

      updateElasticsearchSynonymSet(synonymSetName, synonymRules);

      log.info(
          "동의어 사전 실시간 업데이트 완료 - 환경: {}, synonym_set: {}, 규칙 수: {}",
          environmentType.getDescription(),
          synonymSetName,
          synonymRules.size());

    } catch (Exception e) {
      log.error(
          "동의어 사전 실시간 업데이트 실패 - 환경: {}, synonym_set: {}",
          environmentType.getDescription(),
          synonymSetName,
          e);
      throw new RuntimeException("동의어 사전 실시간 업데이트 실패", e);
    }
  }

  /** 환경별 동의어 규칙 조회 */
  private List<String> getSynonymRules(DictionaryEnvironmentType environmentType) {
    try {
      if (environmentType == DictionaryEnvironmentType.CURRENT) {
        // 현재 편집 중인 사전
        return synonymDictionaryService
            .getSynonymDictionaries(1, 10000, null, "keyword", "asc", environmentType)
            .getContent()
            .stream()
            .map(dict -> dict.getKeyword())
            .collect(Collectors.toList());
      } else {
        // DEV/PROD 스냅샷
        return synonymDictionaryService
            .getSynonymDictionaries(1, 10000, null, "keyword", "asc", environmentType)
            .getContent()
            .stream()
            .map(dict -> dict.getKeyword())
            .collect(Collectors.toList());
      }
    } catch (Exception e) {
      log.error("동의어 규칙 조회 실패 - 환경: {}", environmentType.getDescription(), e);
      return List.of();
    }
  }

  /** Elasticsearch synonym set 업데이트 */
  private void updateElasticsearchSynonymSet(String synonymSetName, List<String> synonymRules)
      throws IOException {
    log.info("Elasticsearch synonym set 업데이트 시작 - 규칙 수: {}", synonymRules.size());

    // SynonymRule 객체 생성
    List<SynonymRule> rules =
        synonymRules.stream()
            .map(rule -> SynonymRule.of(sr -> sr.synonyms(rule)))
            .collect(Collectors.toList());

    // PutSynonymRequest 생성
    PutSynonymRequest request =
        PutSynonymRequest.of(psr -> psr.id(synonymSetName).synonymsSet(rules));

    // Elasticsearch에 요청
    var response = elasticsearchClient.synonyms().putSynonym(request);

    log.info("Elasticsearch synonym set 업데이트 완료");
  }

  /** synonym set 상태 조회 (환경별) */
  public String getSynonymSetStatus(DictionaryEnvironmentType environmentType) {
    try {
      String synonymSetName = getSynonymSetName(environmentType);
      var response = elasticsearchClient.synonyms().getSynonym(gr -> gr.id(synonymSetName));
      return "활성 - 규칙 수: " + response.synonymsSet().size();
    } catch (Exception e) {
      log.warn("Synonym set 상태 조회 실패 - 환경: {}", environmentType.getDescription(), e);
      return "조회 실패";
    }
  }

  /** 현재 synonym set 상태 조회 (기본값: DEV 환경) */
  public String getSynonymSetStatus() {
    return getSynonymSetStatus(DictionaryEnvironmentType.DEV);
  }
}
