package com.yjlee.search.dictionary.synonym.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.synonyms.DeleteSynonymRequest;
import co.elastic.clients.elasticsearch.synonyms.PutSynonymRequest;
import co.elastic.clients.elasticsearch.synonyms.SynonymRule;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.common.service.DictionaryService;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryListResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
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
public class SynonymDictionaryService implements DictionaryService {

  private final SynonymDictionaryRepository repository;
  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentRepository indexEnvironmentRepository;

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
        SynonymDictionary.builder()
            .environmentType(environment)
            .keyword(request.getKeyword())
            .description(request.getDescription())
            .build();

    SynonymDictionary saved = repository.save(dictionary);
    log.info("동의어 사전 생성 완료: {} (ID: {}) - 환경: {}", saved.getKeyword(), saved.getId(), environment);

    return convertToResponse(saved);
  }

  @Transactional(readOnly = true)
  public PageResponse<SynonymDictionaryListResponse> getList(
      int page,
      int size,
      String sortBy,
      String sortDir,
      String search,
      EnvironmentType environmentType) {

    log.debug(
        "동의어 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environmentType);

    Sort sort = createSort(sortBy, sortDir);
    Pageable pageable = PageRequest.of(Math.max(0, page), size, sort);

    Page<SynonymDictionary> dictionaryPage;
    if (search != null && !search.trim().isEmpty()) {
      dictionaryPage =
          repository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
              environmentType, search.trim(), pageable);
    } else {
      dictionaryPage = repository.findByEnvironmentType(environmentType, pageable);
    }

    return PageResponse.from(dictionaryPage.map(this::convertToListResponse));
  }

  @Transactional(readOnly = true)
  public SynonymDictionaryResponse get(Long id, EnvironmentType environment) {
    log.debug("동의어 사전 상세 조회 - ID: {}, 환경: {}", id, environment);

    SynonymDictionary dictionary =
        repository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id));
    return convertToResponse(dictionary);
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
    if (request.getDescription() != null) {
      existing.updateDescription(request.getDescription());
    }

    SynonymDictionary updated = repository.save(existing);
    log.info("동의어 사전 수정 완료: {} - 환경: {}", id, environment);

    return convertToResponse(updated);
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

  @Override
  @Transactional
  public void preIndexing() {
    IndexEnvironment devEnv =
        indexEnvironmentRepository
            .findByEnvironmentType(EnvironmentType.DEV)
            .orElseThrow(() -> new IllegalStateException("DEV 환경이 없습니다"));

    String version = devEnv.getVersion();
    if (version == null) {
      throw new IllegalStateException("DEV 환경에 버전이 설정되지 않았습니다");
    }

    log.info("개발 환경 동의어 사전 배포 시작 - 버전: {}", version);

    List<SynonymDictionary> currentDictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType.CURRENT);

    repository.deleteByEnvironmentType(EnvironmentType.DEV);

    List<SynonymDictionary> devDictionaries =
        currentDictionaries.stream()
            .map(
                dict ->
                    SynonymDictionary.builder()
                        .environmentType(EnvironmentType.DEV)
                        .keyword(dict.getKeyword())
                        .description(dict.getDescription())
                        .build())
            .toList();

    repository.saveAll(devDictionaries);

    // synonym set 생성 (DB에 저장된 이름 사용)
    String synonymSetName = devEnv.getSynonymSetName();
    if (synonymSetName != null) {
      createOrUpdateSynonymSet(synonymSetName, EnvironmentType.DEV);
      log.info("DEV synonym set 생성 완료: {}", synonymSetName);
    }

    log.info("개발 환경 동의어 사전 배포 완료: {}개", devDictionaries.size());
  }

  @Transactional
  public void deleteByEnvironmentType(EnvironmentType environment) {
    log.info("동의어 사전 환경별 삭제 시작 - 환경: {}", environment);
    repository.deleteByEnvironmentType(environment);
    log.info("동의어 사전 환경별 삭제 완료 - 환경: {}", environment);
  }

  @Override
  @Transactional
  public void preDeploy() {
    log.info("운영 환경 동의어 사전 배포 시작");

    List<SynonymDictionary> devDictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType.DEV);

    // 기존 운영 환경 데이터 삭제
    repository.deleteByEnvironmentType(EnvironmentType.PROD);

    // DEV 데이터를 PROD로 복사
    List<SynonymDictionary> prodDictionaries =
        devDictionaries.stream()
            .map(
                dict ->
                    SynonymDictionary.builder()
                        .environmentType(EnvironmentType.PROD)
                        .keyword(dict.getKeyword())
                        .description(dict.getDescription())
                        .build())
            .toList();

    repository.saveAll(prodDictionaries);
    log.info("운영 환경 동의어 사전 배포 완료: {}개", prodDictionaries.size());
  }

  @Override
  public void deployToTemp() {
    log.info("동의어 사전 임시 환경 배포 시작");

    try {
      // 임시 동의어 세트 생성/업데이트
      String tempSynonymSetName = "synonyms-temp-current";
      createOrUpdateSynonymSet(tempSynonymSetName, EnvironmentType.CURRENT);
      log.info("동의어 사전 임시 환경 Elasticsearch 동기화 완료 - synonymSet: {}", tempSynonymSetName);

    } catch (Exception e) {
      log.error("동의어 사전 임시 환경 배포 실패", e);
      throw new RuntimeException("동의어 사전 임시 환경 배포 실패", e);
    }
  }

  private SynonymDictionaryResponse convertToResponse(SynonymDictionary entity) {
    return SynonymDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
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

  @Override
  public void realtimeSync(EnvironmentType environment) {
    log.info("동의어 사전 실시간 동기화 - 환경: {}", environment);

    try {
      String synonymSetName;

      if (environment == EnvironmentType.CURRENT) {
        // CURRENT 환경은 synonyms-nori-current 사용
        synonymSetName = "synonyms-nori-current";
      } else {
        // DEV/PROD 환경은 현재 인덱스 버전에 맞는 synonym set 사용
        try {
          IndexEnvironment indexEnvironment =
              indexEnvironmentRepository
                  .findByEnvironmentType(environment)
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException(
                              environment.getDescription() + " 환경을 찾을 수 없습니다."));
          String indexName = indexEnvironment.getIndexName();

          // 인덱스 이름에서 버전 추출 (예: products_v15 -> v15)
          String version = indexName.substring(indexName.lastIndexOf("_v") + 1);
          synonymSetName = "synonyms-nori-" + version;
        } catch (Exception e) {
          // 인덱스가 아직 활성화되지 않은 경우 (예: 색인 중)
          log.warn("동의어 사전 실시간 동기화 건너뛰기 - 환경: {} (인덱스 미활성화)", environment);
          return;
        }
      }

      // Elasticsearch synonym set 업데이트
      createOrUpdateSynonymSet(synonymSetName, environment);
      log.info("동의어 사전 Elasticsearch 동기화 완료 - 환경: {}, synonymSet: {}", environment, synonymSetName);

    } catch (Exception e) {
      log.error("동의어 사전 실시간 동기화 실패 - 환경: {}", environment, e);
      throw new RuntimeException("동의어 사전 실시간 동기화 실패", e);
    }
  }

  public String getDictionaryType() {
    return "SYNONYM";
  }

  private SynonymDictionaryListResponse convertToListResponse(SynonymDictionary entity) {
    return SynonymDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private Sort createSort(String sortBy, String sortDir) {
    if (sortBy == null || sortBy.trim().isEmpty()) {
      sortBy = "updatedAt";
    }
    if (sortDir == null || sortDir.trim().isEmpty()) {
      sortDir = "desc";
    }

    String[] allowedFields = {"keyword", "createdAt", "updatedAt"};
    boolean isValidField = false;
    for (String field : allowedFields) {
      if (field.equals(sortBy)) {
        isValidField = true;
        break;
      }
    }

    if (!isValidField) {
      log.warn("허용되지 않은 정렬 필드: {}. 기본값 updatedAt 사용", sortBy);
      sortBy = "updatedAt";
    }

    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return Sort.by(direction, sortBy);
  }

  public void createOrUpdateSynonymSet(String setName, EnvironmentType environmentType) {
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
      DeleteSynonymRequest request = DeleteSynonymRequest.of(d -> d.id(setName));
      elasticsearchClient.synonyms().deleteSynonym(request);
      log.info("동의어 세트 삭제 완료 - set: {}", setName);
    } catch (Exception e) {
      log.warn("동의어 세트 삭제 실패(무시) - set: {}, msg: {}", setName, e.getMessage());
    }
  }

  /** 환경별 동의어 규칙 조회 */
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
