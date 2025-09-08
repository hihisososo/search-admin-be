package com.yjlee.search.dictionary.synonym.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.service.DictionaryService;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryListResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
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

  @Override
  public String getDictionaryTypeEnum() {
    return "SYNONYM";
  }

  @Transactional
  public SynonymDictionaryResponse create(
      SynonymDictionaryCreateRequest request, DictionaryEnvironmentType environment) {
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
      DictionaryEnvironmentType environmentType) {

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
  public SynonymDictionaryResponse get(Long id, DictionaryEnvironmentType environment) {
    log.debug("동의어 사전 상세 조회 - ID: {}, 환경: {}", id, environment);

    SynonymDictionary dictionary =
        repository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id));
    return convertToResponse(dictionary);
  }

  @Transactional
  public SynonymDictionaryResponse update(
      Long id, SynonymDictionaryUpdateRequest request, DictionaryEnvironmentType environment) {
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
  public void delete(Long id, DictionaryEnvironmentType environment) {
    log.info("동의어 사전 삭제 요청: {} - 환경: {}", id, environment);

    if (!repository.existsById(id)) {
      throw new EntityNotFoundException("사전을 찾을 수 없습니다: " + id);
    }

    repository.deleteById(id);
    log.info("동의어 사전 삭제 완료: {} - 환경: {}", id, environment);
  }

  @Override
  @Transactional
  public void deployToDev() {
    log.info("개발 환경 동의어 사전 배포 시작");

    List<SynonymDictionary> currentDictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(DictionaryEnvironmentType.CURRENT);
    if (currentDictionaries.isEmpty()) {
      log.warn("배포할 동의어 사전이 없습니다.");
      return;
    }

    // 기존 개발 환경 데이터 삭제
    repository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    // CURRENT 데이터를 DEV로 복사
    List<SynonymDictionary> devDictionaries =
        currentDictionaries.stream()
            .map(
                dict ->
                    SynonymDictionary.builder()
                        .environmentType(DictionaryEnvironmentType.DEV)
                        .keyword(dict.getKeyword())
                        .description(dict.getDescription())
                        .build())
            .toList();

    repository.saveAll(devDictionaries);
    log.info("개발 환경 동의어 사전 배포 완료: {}개", devDictionaries.size());
  }

  @Transactional
  public void deleteByEnvironmentType(DictionaryEnvironmentType environment) {
    log.info("동의어 사전 환경별 삭제 시작 - 환경: {}", environment);
    repository.deleteByEnvironmentType(environment);
    log.info("동의어 사전 환경별 삭제 완료 - 환경: {}", environment);
  }

  @Override
  @Transactional
  public void deployToProd() {
    log.info("운영 환경 동의어 사전 배포 시작");

    List<SynonymDictionary> currentDictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(DictionaryEnvironmentType.CURRENT);
    if (currentDictionaries.isEmpty()) {
      throw new IllegalStateException("배포할 동의어 사전이 없습니다.");
    }

    // 기존 운영 환경 데이터 삭제
    repository.deleteByEnvironmentType(DictionaryEnvironmentType.PROD);

    // CURRENT 데이터를 PROD로 복사
    List<SynonymDictionary> prodDictionaries =
        currentDictionaries.stream()
            .map(
                dict ->
                    SynonymDictionary.builder()
                        .environmentType(DictionaryEnvironmentType.PROD)
                        .keyword(dict.getKeyword())
                        .description(dict.getDescription())
                        .build())
            .toList();

    repository.saveAll(prodDictionaries);
    log.info("운영 환경 동의어 사전 배포 완료: {}개", prodDictionaries.size());
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

  @Override
  public String getDictionaryContent(DictionaryEnvironmentType environment) {
    List<SynonymDictionary> dictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(environment);
    StringBuilder content = new StringBuilder();

    for (SynonymDictionary dict : dictionaries) {
      content.append(dict.getKeyword());
      if (dict.getDescription() != null && !dict.getDescription().isEmpty()) {
        content.append(" => ").append(dict.getDescription());
      }
      content.append("\n");
    }

    return content.toString();
  }

  @Override
  public void realtimeSync(DictionaryEnvironmentType environment) {
    log.info("동의어 사전 실시간 동기화 - 환경: {}", environment);
    // TODO: 캐시 업데이트 로직 구현
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
}
