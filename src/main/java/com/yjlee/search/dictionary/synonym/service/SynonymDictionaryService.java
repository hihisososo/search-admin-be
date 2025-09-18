package com.yjlee.search.dictionary.synonym.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.synonyms.DeleteSynonymRequest;
import co.elastic.clients.elasticsearch.synonyms.PutSynonymRequest;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.service.IndexEnvironmentService;
import com.yjlee.search.dictionary.common.model.DictionaryData;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryListResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SynonymDictionaryService {

  private final SynonymDictionaryRepository repository;
  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentService indexEnvironmentService;

  public String getDictionaryTypeEnum() {
    return "SYNONYM";
  }

  @Transactional
  public SynonymDictionaryResponse create(
      SynonymDictionaryCreateRequest request, EnvironmentType environment) {
    log.info("동의어 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);

    if (repository.existsByKeywordAndEnvironmentType(request.getKeyword(), environment)) {
      throw new IllegalArgumentException("이미 존재하는 키워드입니다: " + request.getKeyword());
    }

    SynonymDictionary dictionary =
        SynonymDictionary.of(request.getKeyword(), environment);

    SynonymDictionary saved = repository.save(dictionary);
    log.info("동의어 사전 생성 완료: {} (ID: {}) - 환경: {}", saved.getKeyword(), saved.getId(), environment);

    return SynonymDictionaryResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public PageResponse<SynonymDictionaryListResponse> getList(
      Pageable pageable,
      String search,
      EnvironmentType environmentType) {

    log.debug(
        "동의어 사전 목록 조회 - page: {}, size: {}, search: {}, environment: {}",
        pageable.getPageNumber(),
        pageable.getPageSize(),
        search,
        environmentType);

    Page<SynonymDictionary> dictionaryPage;
    if (search != null && !search.trim().isEmpty()) {
      dictionaryPage =
          repository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
              environmentType, search.trim(), pageable);
    } else {
      dictionaryPage = repository.findByEnvironmentType(environmentType, pageable);
    }

    return PageResponse.from(dictionaryPage.map(SynonymDictionaryListResponse::from));
  }

  @Transactional(readOnly = true)
  public SynonymDictionaryResponse get(Long id, EnvironmentType environment) {
    log.debug("동의어 사전 상세 조회 - ID: {}, 환경: {}", id, environment);

    SynonymDictionary dictionary =
        repository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id));
    return SynonymDictionaryResponse.from(dictionary);
  }

  @Transactional
  public SynonymDictionaryResponse update(
      Long id, SynonymDictionaryUpdateRequest request, EnvironmentType environment) {
    log.info("동의어 사전 수정 요청: {} - 환경: {}", id, environment);

    SynonymDictionary existing =
        repository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id));

    if (request.getKeyword() != null) {
      existing.updateKeyword(request.getKeyword());
    }

    SynonymDictionary updated = repository.save(existing);
    log.info("동의어 사전 수정 완료: {} - 환경: {}", id, environment);

    return SynonymDictionaryResponse.from(updated);
  }

  @Transactional
  public void delete(Long id, EnvironmentType environment) {
    log.info("동의어 사전 삭제 요청: {} - 환경: {}", id, environment);

    if (!repository.existsById(id)) {
      throw new EntityNotFoundException("사전을 찾을 수 없습니다: " + id);
    }

    repository.deleteById(id);
    log.info("동의어 사전 삭제 완료: {} - 환경: {}", id, environment);
  }

  public void preIndexing(DictionaryData data) {
    String version = data.getVersion();
    List<SynonymDictionary> synonyms = data.getSynonyms();
    log.info("동의어 사전 배포 시작 - 버전: {}, 규칙 수: {}", version, synonyms.size());

    try {
      String synonymSetName = "synonyms-nori-" + version;
      List<String> synonymRules = new ArrayList<>();

      for (SynonymDictionary dict : synonyms) {
        synonymRules.add(dict.getKeyword());
      }

      updateElasticsearchSynonymSet(synonymSetName, synonymRules);
      log.info("동의어 배포 완료 - synonymSet: {}", synonymSetName);

    } catch (Exception e) {
      log.error("동의어 사전 메모리 기반 배포 실패", e);
      throw new RuntimeException("동의어 사전 배포 실패: " + e.getMessage(), e);
    }
  }

  @Transactional
  public void deleteByEnvironmentType(EnvironmentType environment) {
    log.info("동의어 사전 환경별 삭제 시작 - 환경: {}", environment);
    repository.deleteByEnvironmentType(environment);
    log.info("동의어 사전 환경별 삭제 완료 - 환경: {}", environment);
  }

  @Transactional
  public void copyToEnvironment(EnvironmentType from, EnvironmentType to) {
    List<SynonymDictionary> sourceDictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(from);

    if (sourceDictionaries.isEmpty()) {
      log.warn("{} 환경에서 {} 환경으로 배포할 동의어 사전이 없음 - 빈 사전으로 처리", from, to);
    }

    deleteByEnvironmentType(to);

    List<SynonymDictionary> targetDictionaries =
        sourceDictionaries.stream()
            .map(
                dict ->
                    SynonymDictionary.builder()
                        .environmentType(to)
                        .keyword(dict.getKeyword())
                        .build())
            .toList();

    repository.saveAll(targetDictionaries);
    log.info("{} 환경 동의어 사전 배포 완료: {}개", to, targetDictionaries.size());
  }

  @Transactional
  public void saveToEnvironment(List<SynonymDictionary> sourceData, EnvironmentType targetEnv) {
    if (sourceData == null || sourceData.isEmpty()) {
      log.info("동의어 사전 데이터가 비어있음 - {} 환경 스킵", targetEnv);
      return;
    }

    List<SynonymDictionary> targetDictionaries =
        sourceData.stream()
            .map(dict -> SynonymDictionary.copyWithEnvironment(dict, targetEnv))
            .toList();

    repository.saveAll(targetDictionaries);
    log.info("{} 환경 동의어 사전 저장 완료: {}개", targetEnv, targetDictionaries.size());
  }

  public void deployToTemp(DictionaryData data) {
    if (data == null || data.getSynonyms().isEmpty()) {
      log.info("동의어 사전 데이터가 비어있음 - 임시 환경 배포 건너뛰기");
      return;
    }
    String tempSynonymSetName = "synonyms-temp-current";
    List<String> synonymRules = new ArrayList<>();

    for (SynonymDictionary dict : data.getSynonyms()) {
      synonymRules.add(dict.getKeyword());
    }

    updateElasticsearchSynonymSet(tempSynonymSetName, synonymRules);
    log.info("동의어 사전 배포 완료 - synonymSet: {}", tempSynonymSetName);
  }

  public String getDictionaryContent(EnvironmentType environment) {
    List<SynonymDictionary> dictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(environment);
    StringBuilder content = new StringBuilder();

    for (SynonymDictionary dict : dictionaries) {
      content.append(dict.getKeyword());
      content.append("\n");
    }

    return content.toString();
  }

  public void realtimeSync(EnvironmentType environment) {
    log.info("동의어 사전 실시간 동기화 - 환경: {}", environment);

    try {
      String synonymSetName;

      if (environment == EnvironmentType.CURRENT) {
        synonymSetName = "synonyms-nori-current";
      } else {
        IndexEnvironment indexEnvironment = indexEnvironmentService.getEnvironment(environment);
        synonymSetName = indexEnvironment.getSynonymSetName();
      }

      createOrUpdateSynonymSet(environment);
      log.info("동의어 사전 실시간 동기화 완료 - 환경: {}, synonymSet: {}", environment, synonymSetName);

    } catch (Exception e) {
      log.error("동의어 사전 실시간 동기화 실패 - 환경: {}", environment, e);
      throw new RuntimeException("동의어 사전 실시간 동기화 실패", e);
    }
  }

  public String getDictionaryType() {
    return "SYNONYM";
  }


  public void createOrUpdateSynonymSet(EnvironmentType environmentType) {
    try {
      List<String> synonymRules = getSynonymRules(environmentType);
      String synonymSetName =
          indexEnvironmentService.getEnvironment(environmentType).getSynonymSetName();
      updateElasticsearchSynonymSet(synonymSetName, synonymRules);
      log.info("버전 동의어 세트 생성/업데이트 완료 - set: {}, 규칙 수: {}", synonymSetName, synonymRules.size());
    } catch (Exception e) {
      log.error("버전 동의어 세트 생성/업데이트 실패", e);
      throw new RuntimeException("버전 동의어 세트 생성/업데이트 실패", e);
    }
  }

  public void syncWithPreloadedData(List<SynonymDictionary> synonyms, String synonymSetName) {
    try {
      List<String> synonymRules =
          synonyms.stream().map(SynonymDictionary::getKeyword).collect(Collectors.toList());

      updateElasticsearchSynonymSet(synonymSetName, synonymRules);
      log.info("Preloaded 동의어 세트 동기화 완료 - set: {}, 규칙 수: {}", synonymSetName, synonymRules.size());
    } catch (Exception e) {
      log.error("Preloaded 동의어 세트 동기화 실패", e);
      throw new RuntimeException("Preloaded 동의어 세트 동기화 실패", e);
    }
  }

  public void deleteSynonymSet(String setName) {
    try {
      DeleteSynonymRequest request = DeleteSynonymRequest.of(d -> d.id(setName));
      elasticsearchClient.synonyms().deleteSynonym(request);
      log.info("동의어 세트 삭제 완료 - set: {}", setName);
    } catch (Exception e) {
      log.warn("동의어 세트 삭제 실패(무시) - set: {}, msg: {}", setName, e.getMessage());
    }
  }

  private List<String> getSynonymRules(EnvironmentType environmentType) {
    try {
      List<SynonymDictionary> dictionaries =
          repository.findByEnvironmentTypeOrderByKeywordAsc(environmentType);
      return dictionaries.stream().map(dict -> dict.getKeyword()).collect(Collectors.toList());
    } catch (Exception e) {
      log.error("동의어 규칙 조회 실패 - 환경: {}", environmentType.getDescription(), e);
      return List.of();
    }
  }

  private void updateElasticsearchSynonymSet(String synonymSetName, List<String> synonymRules) {
    log.info("Elasticsearch synonym 업데이트 시작 - 규칙 수: {}", synonymRules.size());

    List<SynonymRule> rules =
        synonymRules.stream()
            .map(rule -> SynonymRule.of(sr -> sr.synonyms(rule)))
            .collect(Collectors.toList());

    PutSynonymRequest request =
        PutSynonymRequest.of(psr -> psr.id(synonymSetName).synonymsSet(rules));

    try {
      elasticsearchClient.synonyms().putSynonym(request);
    } catch (Exception e) {
      throw new RuntimeException("Elastcisearch synonym 업로드 실패");
    }

    log.info("Elasticsearch synonym set 업데이트 완료");
  }
}
