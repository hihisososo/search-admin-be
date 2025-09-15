package com.yjlee.search.dictionary.typo.service;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.common.service.DictionaryService;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryCreateRequest;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryListResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryUpdateRequest;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionaryRepository;
import com.yjlee.search.search.service.typo.TypoCorrectionService;
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
public class TypoCorrectionDictionaryService implements DictionaryService {

  private final TypoCorrectionDictionaryRepository repository;
  private final TypoCorrectionService typoCorrectionService;

  public String getDictionaryTypeEnum() {
    return "TYPO";
  }

  /** 오타교정 사전 생성 */
  @Transactional
  public TypoCorrectionDictionaryResponse createTypoCorrectionDictionary(
      TypoCorrectionDictionaryCreateRequest request, EnvironmentType environment) {
    log.info(
        "오타교정 사전 생성 요청: {} -> {} - 환경: {}",
        request.getKeyword(),
        request.getCorrectedWord(),
        environment);

    if (repository.existsByKeywordAndEnvironmentType(request.getKeyword(), environment)) {
      throw new IllegalArgumentException("이미 존재하는 키워드입니다: " + request.getKeyword());
    }

    TypoCorrectionDictionary dictionary =
        TypoCorrectionDictionary.builder()
            .environmentType(environment)
            .keyword(request.getKeyword())
            .correctedWord(request.getCorrectedWord())
            .description(request.getDescription())
            .build();

    TypoCorrectionDictionary saved = repository.save(dictionary);
    log.info("오타교정 사전 생성 완료: {} (ID: {}) - 환경: {}", saved.getKeyword(), saved.getId(), environment);

    return convertToResponse(saved);
  }

  /** 오타교정 사전 목록 조회 (페이징, 검색, 정렬, 환경별) */
  @Transactional(readOnly = true)
  public PageResponse<TypoCorrectionDictionaryListResponse> getTypoCorrectionDictionaries(
      int page,
      int size,
      String search,
      String sortBy,
      String sortDir,
      EnvironmentType environmentType) {

    log.debug(
        "오타교정 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environmentType);

    Sort sort = createSort(sortBy, sortDir);
    Pageable pageable = PageRequest.of(Math.max(0, page), size, sort);

    Page<TypoCorrectionDictionary> dictionaryPage;
    if (search != null && !search.trim().isEmpty()) {
      dictionaryPage =
          repository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
              environmentType, search.trim(), pageable);
    } else {
      dictionaryPage = repository.findByEnvironmentType(environmentType, pageable);
    }

    return PageResponse.from(dictionaryPage.map(this::toTypoCorrectionDictionaryListResponse));
  }

  /** 오타교정 사전 상세 조회 */
  @Transactional(readOnly = true)
  public TypoCorrectionDictionaryResponse getTypoCorrectionDictionaryDetail(
      Long dictionaryId, EnvironmentType environment) {
    log.debug("오타교정 사전 상세 조회 요청: {} - 환경: {}", dictionaryId, environment);

    TypoCorrectionDictionary dictionary =
        repository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오타교정 사전입니다: " + dictionaryId));
    return convertToResponse(dictionary);
  }

  /** 오타교정 사전 수정 */
  @Transactional
  public TypoCorrectionDictionaryResponse updateTypoCorrectionDictionary(
      Long dictionaryId,
      TypoCorrectionDictionaryUpdateRequest request,
      EnvironmentType environment) {
    log.info("오타교정 사전 수정 요청: {} - 환경: {}", dictionaryId, environment);

    TypoCorrectionDictionary existing =
        repository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오타교정 사전입니다: " + dictionaryId));

    if (request.getKeyword() != null) {
      existing.updateKeyword(request.getKeyword());
    }
    if (request.getCorrectedWord() != null) {
      existing.updateCorrectedWord(request.getCorrectedWord());
    }
    if (request.getDescription() != null) {
      existing.updateDescription(request.getDescription());
    }

    TypoCorrectionDictionary updated = repository.save(existing);
    log.info("오타교정 사전 수정 완료: {} - 환경: {}", dictionaryId, environment);

    return convertToResponse(updated);
  }

  /** 오타교정 사전 삭제 */
  @Transactional
  public void deleteTypoCorrectionDictionary(Long dictionaryId, EnvironmentType environment) {
    log.info("오타교정 사전 삭제 요청: {} - 환경: {}", dictionaryId, environment);

    if (!repository.existsById(dictionaryId)) {
      throw new IllegalArgumentException("존재하지 않는 오타교정 사전입니다: " + dictionaryId);
    }

    repository.deleteById(dictionaryId);
    log.info("오타교정 사전 삭제 완료: {} - 환경: {}", dictionaryId, environment);
  }

  @Override
  @Transactional
  public void preIndexing() {
    log.info("개발 환경 오타교정 사전 배포 시작");

    List<TypoCorrectionDictionary> currentDictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType.CURRENT);
    if (currentDictionaries.isEmpty()) {
      log.warn("배포할 오타교정 사전이 없습니다.");
      return;
    }

    // 기존 개발 환경 데이터 삭제
    repository.deleteByEnvironmentType(EnvironmentType.DEV);

    // CURRENT 데이터를 DEV로 복사
    List<TypoCorrectionDictionary> devDictionaries =
        currentDictionaries.stream()
            .map(
                dict ->
                    TypoCorrectionDictionary.builder()
                        .environmentType(EnvironmentType.DEV)
                        .keyword(dict.getKeyword())
                        .correctedWord(dict.getCorrectedWord())
                        .description(dict.getDescription())
                        .build())
            .toList();

    repository.saveAll(devDictionaries);
    log.info("개발 환경 오타교정 사전 배포 완료: {}개", devDictionaries.size());
  }

  @Override
  @Transactional
  public void preDeploy() {
    log.info("운영 환경 오타교정 사전 배포 시작");

    List<TypoCorrectionDictionary> devDictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(EnvironmentType.DEV);

    // PROD 배포시 소스가 비어있어도 허용 (빈 사전도 유효함)
    if (devDictionaries.isEmpty()) {
      log.warn("DEV 환경에서 PROD 환경으로 배포할 오타교정 사전이 없음 - 빈 사전으로 처리");
    }

    // 기존 운영 환경 데이터 삭제
    repository.deleteByEnvironmentType(EnvironmentType.PROD);

    // DEV 데이터를 PROD로 복사
    List<TypoCorrectionDictionary> prodDictionaries =
        devDictionaries.stream()
            .map(
                dict ->
                    TypoCorrectionDictionary.builder()
                        .environmentType(EnvironmentType.PROD)
                        .keyword(dict.getKeyword())
                        .correctedWord(dict.getCorrectedWord())
                        .description(dict.getDescription())
                        .build())
            .toList();

    repository.saveAll(prodDictionaries);
    log.info("운영 환경 오타교정 사전 배포 완료: {}개", prodDictionaries.size());
  }

  @Override
  public void deployToTemp() {
    // 오타교정 사전은 캐시 기반으로 동작하므로 임시 환경 배포 불필요
    log.debug("오타교정 사전 임시 환경 배포 건너뛰기 - 캐시 기반 동작");
  }

  /** 정렬 조건 생성 */
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

  /** Entity to Response 변환 */
  private TypoCorrectionDictionaryResponse convertToResponse(TypoCorrectionDictionary entity) {
    return TypoCorrectionDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .correctedWord(entity.getCorrectedWord())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Entity to ListResponse 변환 */
  private TypoCorrectionDictionaryListResponse toTypoCorrectionDictionaryListResponse(
      TypoCorrectionDictionary entity) {
    return TypoCorrectionDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .correctedWord(entity.getCorrectedWord())
        .description(entity.getDescription())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  public String getDictionaryContent(EnvironmentType environment) {
    List<TypoCorrectionDictionary> dictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(environment);
    StringBuilder content = new StringBuilder();

    for (TypoCorrectionDictionary dict : dictionaries) {
      content.append(dict.getKeyword()).append(" => ").append(dict.getCorrectedWord());
      if (dict.getDescription() != null && !dict.getDescription().isEmpty()) {
        content.append(" (").append(dict.getDescription()).append(")");
      }
      content.append("\n");
    }

    return content.toString();
  }

  @Override
  public void realtimeSync(EnvironmentType environment) {
    log.info("오타교정 사전 실시간 동기화 - 환경: {}", environment);
    typoCorrectionService.updateCacheRealtime(environment);
    log.info("오타교정 캐시 업데이트 완료 - 환경: {}", environment);
  }

  public String getDictionaryType() {
    return "TYPO_CORRECTION";
  }

  @Transactional
  public void deleteByEnvironmentType(EnvironmentType environment) {
    log.info("오타교정 사전 환경별 삭제 시작 - 환경: {}", environment);
    repository.deleteByEnvironmentType(environment);
    log.info("오타교정 사전 환경별 삭제 완료 - 환경: {}", environment);
  }
}
