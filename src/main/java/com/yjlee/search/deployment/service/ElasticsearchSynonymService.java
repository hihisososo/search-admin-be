package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.synonyms.PutSynonymRequest;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import java.io.IOException;
import java.util.ArrayList;
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
  private final SynonymDictionaryRepository synonymDictionaryRepository;

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

  public List<String> getAllSynonymSets() {
    try {
      var response = elasticsearchClient.synonyms().getSynonymsSets(g -> g);

      List<String> synonymSetNames =
          response.results().stream()
              .map(result -> result.synonymsSet())
              .collect(Collectors.toList());

      log.info("전체 동의어 세트 조회 완료 - 개수: {}", synonymSetNames.size());
      return synonymSetNames;
    } catch (Exception e) {
      log.error("동의어 세트 목록 조회 실패", e);
      return new ArrayList<>();
    }
  }

  public List<String> deleteSynonymSets(List<String> setNames) {
    List<String> deletedSets = new ArrayList<>();

    for (String setName : setNames) {
      try {
        var request =
            co.elastic.clients.elasticsearch.synonyms.DeleteSynonymRequest.of(d -> d.id(setName));
        elasticsearchClient.synonyms().deleteSynonym(request);
        deletedSets.add(setName);
        log.info("동의어 세트 삭제 성공 - set: {}", setName);
      } catch (Exception e) {
        log.warn("동의어 세트 삭제 실패 - set: {}, msg: {}", setName, e.getMessage());
      }
    }

    log.info("동의어 세트 일괄 삭제 완료 - 요청: {}개, 성공: {}개", setNames.size(), deletedSets.size());
    return deletedSets;
  }

  /** 환경별 동의어 규칙 조회 */
  private List<String> getSynonymRules(DictionaryEnvironmentType environmentType) {
    try {
      // Repository에서 직접 데이터 조회
      List<SynonymDictionary> dictionaries =
          synonymDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environmentType);

      return dictionaries.stream().map(dict -> dict.getKeyword()).collect(Collectors.toList());
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
