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

  public void createOrUpdateSynonymSet(String setName, DictionaryEnvironmentType environmentType) {
    try {
      List<String> synonymRules = getSynonymRules(environmentType);
      updateElasticsearchSynonymSet(setName, synonymRules);
      log.info("버전 동의어 세트 생성/업데이트 완료 - set: {}, 규칙 수: {}", setName, synonymRules.size());
    } catch (Exception e) {
      log.error("버전 동의어 세트 생성/업데이트 실패 - set: {}", setName, e);
      throw new RuntimeException("버전 동의어 세트 생성/업데이트 실패", e);
    }
  }

  public void deleteSynonymSet(String setName) {
    try {
      var request =
          co.elastic.clients.elasticsearch.synonyms.DeleteSynonymRequest.of(d -> d.id(setName));
      elasticsearchClient.synonyms().deleteSynonym(request);
      log.info("동의어 세트 삭제 완료 - set: {}", setName);
    } catch (Exception e) {
      log.warn("동의어 세트 삭제 실패(무시) - set: {}, msg: {}", setName, e.getMessage());
    }
  }

  /** 환경별 동의어 규칙 조회 */
  private List<String> getSynonymRules(DictionaryEnvironmentType environmentType) {
    try {
      if (environmentType == DictionaryEnvironmentType.CURRENT) {
        // 현재 편집 중인 사전
        return synonymDictionaryService
            .getSynonymDictionaries(0, 10000, null, "keyword", "asc", environmentType)
            .getContent()
            .stream()
            .map(dict -> dict.getKeyword())
            .collect(Collectors.toList());
      } else {
        // DEV/PROD 스냅샷
        return synonymDictionaryService
            .getSynonymDictionaries(0, 10000, null, "keyword", "asc", environmentType)
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

    PutSynonymRequest request =
        PutSynonymRequest.of(psr -> psr.id(synonymSetName).synonymsSet(rules));

    elasticsearchClient.synonyms().putSynonym(request);

    log.info("Elasticsearch synonym set 업데이트 완료");
  }
}
