package com.yjlee.search.dictionary.typo.service;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryCreateRequest;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryListResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryUpdateRequest;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionaryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypoCorrectionDictionaryService {

  private final TypoCorrectionDictionaryRepository repository;

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
        TypoCorrectionDictionary.of(request.getKeyword(), request.getCorrectedWord(), environment);

    TypoCorrectionDictionary saved = repository.save(dictionary);
    log.info("오타교정 사전 생성 완료: {} (ID: {}) - 환경: {}", saved.getKeyword(), saved.getId(), environment);

    return TypoCorrectionDictionaryResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public PageResponse<TypoCorrectionDictionaryListResponse> getTypoCorrectionDictionaries(
      Pageable pageable, String search, EnvironmentType environmentType) {

    log.debug(
        "오타교정 사전 목록 조회 - page: {}, size: {}, search: {}, environment: {}",
        pageable.getPageNumber(),
        pageable.getPageSize(),
        search,
        environmentType);

    Page<TypoCorrectionDictionary> dictionaryPage;
    if (search != null && !search.trim().isEmpty()) {
      dictionaryPage =
          repository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
              environmentType, search.trim(), pageable);
    } else {
      dictionaryPage = repository.findByEnvironmentType(environmentType, pageable);
    }

    return PageResponse.from(dictionaryPage.map(TypoCorrectionDictionaryListResponse::from));
  }

  @Transactional(readOnly = true)
  public TypoCorrectionDictionaryResponse getTypoCorrectionDictionaryDetail(
      Long dictionaryId, EnvironmentType environment) {
    log.debug("오타교정 사전 상세 조회 요청: {} - 환경: {}", dictionaryId, environment);

    TypoCorrectionDictionary dictionary =
        repository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오타교정 사전입니다: " + dictionaryId));
    return TypoCorrectionDictionaryResponse.from(dictionary);
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

    TypoCorrectionDictionary updated = repository.save(existing);
    log.info("오타교정 사전 수정 완료: {} - 환경: {}", dictionaryId, environment);

    return TypoCorrectionDictionaryResponse.from(updated);
  }

  /** 오타교정 사전 삭제 */
  @Transactional
  public void deleteTypoCorrectionDictionary(Long dictionaryId, EnvironmentType environment) {
    log.info("오타교정 사전 삭제 요청: {} - 환경: {}", dictionaryId, environment);

    if (!repository.existsById(dictionaryId)) {
      throw new EntityNotFoundException("존재하지 않는 오타교정 사전입니다: " + dictionaryId);
    }

    repository.deleteById(dictionaryId);
    log.info("오타교정 사전 삭제 완료: {} - 환경: {}", dictionaryId, environment);
  }

  public String getDictionaryContent(EnvironmentType environment) {
    List<TypoCorrectionDictionary> dictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(environment);
    StringBuilder content = new StringBuilder();

    for (TypoCorrectionDictionary dict : dictionaries) {
      content.append(dict.getKeyword()).append(" => ").append(dict.getCorrectedWord());
      content.append("\n");
    }

    return content.toString();
  }

  @Transactional
  public void deleteByEnvironmentType(EnvironmentType environment) {
    log.info("오타교정 사전 환경별 삭제 시작 - 환경: {}", environment);
    repository.deleteByEnvironmentType(environment);
    log.info("오타교정 사전 환경별 삭제 완료 - 환경: {}", environment);
  }

  @Transactional
  public void saveToEnvironment(
      List<TypoCorrectionDictionary> sourceData, EnvironmentType targetEnv) {
    // 기존 환경 데이터 삭제
    deleteByEnvironmentType(targetEnv);
    repository.flush(); // delete를 DB에 즉시 반영

    if (sourceData == null || sourceData.isEmpty()) {
      return;
    }

    List<TypoCorrectionDictionary> targetDictionaries =
        sourceData.stream()
            .map(
                dict ->
                    TypoCorrectionDictionary.builder()
                        .environmentType(targetEnv)
                        .keyword(dict.getKeyword())
                        .correctedWord(dict.getCorrectedWord())
                        .build())
            .toList();

    repository.saveAll(targetDictionaries);
    log.info("{} 환경 오타교정 사전 저장 완료: {}개", targetEnv, targetDictionaries.size());
  }
}
