package com.yjlee.search.dictionary.typo.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryCreateRequest;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryListResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryUpdateRequest;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionarySnapshot;
import com.yjlee.search.dictionary.typo.recommendation.model.TypoCorrectionRecommendation;
import com.yjlee.search.dictionary.typo.recommendation.repository.TypoCorrectionRecommendationRepository;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionaryRepository;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionarySnapshotRepository;
import java.util.List;
import java.util.ArrayList;
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
public class TypoCorrectionDictionaryService {

  private final TypoCorrectionDictionaryRepository repository;
  private final TypoCorrectionDictionarySnapshotRepository snapshotRepository;
  private final TypoCorrectionRecommendationRepository recommendationRepository;

  /** 오타교정 사전 생성 */
  @Transactional
  public TypoCorrectionDictionaryResponse createTypoCorrectionDictionary(
      TypoCorrectionDictionaryCreateRequest request, DictionaryEnvironmentType environment) {
    log.info(
        "오타교정 사전 생성 요청: {} -> {} - 환경: {}",
        request.getKeyword(),
        request.getCorrectedWord(),
        environment);

    TypoCorrectionDictionary dictionary =
        TypoCorrectionDictionary.builder()
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
      DictionaryEnvironmentType environmentType) {

    log.debug(
        "오타교정 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environmentType);

    // 환경 타입에 따라 현재 사전 또는 스냅샷에서 조회
    if (environmentType == null || environmentType == DictionaryEnvironmentType.CURRENT) {
      return getTypoCorrectionDictionariesFromCurrent(page, size, search, sortBy, sortDir);
    } else {
      return getTypoCorrectionDictionariesFromSnapshot(
          page, size, search, sortBy, sortDir, environmentType);
    }
  }

  /** 현재 오타교정 사전 목록 조회 */
  private PageResponse<TypoCorrectionDictionaryListResponse>
      getTypoCorrectionDictionariesFromCurrent(
          int page, int size, String search, String sortBy, String sortDir) {

    Sort sort = createSort(sortBy, sortDir, false);
    Pageable pageable = PageRequest.of(Math.max(0, page), size, sort);

    Page<TypoCorrectionDictionary> dictionaryPage;
    if (search != null && !search.trim().isEmpty()) {
      dictionaryPage = repository.findByKeywordContainingIgnoreCase(search.trim(), pageable);
    } else {
      dictionaryPage = repository.findAll(pageable);
    }

    return PageResponse.from(dictionaryPage.map(this::toTypoCorrectionDictionaryListResponse));
  }

  /** 스냅샷에서 오타교정 사전 목록 조회 */
  private PageResponse<TypoCorrectionDictionaryListResponse>
      getTypoCorrectionDictionariesFromSnapshot(
          int page,
          int size,
          String search,
          String sortBy,
          String sortDir,
          DictionaryEnvironmentType environmentType) {

    Sort sort = createSort(sortBy, sortDir, true);
    Pageable pageable = PageRequest.of(Math.max(0, page), size, sort);

    Page<TypoCorrectionDictionarySnapshot> snapshotPage;
    if (search != null && !search.trim().isEmpty()) {
      snapshotPage =
          snapshotRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCaseOrderByKeywordAsc(
              environmentType, search.trim(), pageable);
    } else {
      snapshotPage =
          snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(environmentType, pageable);
    }

    return PageResponse.from(
        snapshotPage.map(snapshot -> toTypoCorrectionDictionaryListResponseFromSnapshot(snapshot)));
  }

  /** 오타교정 사전 상세 조회 */
  @Transactional(readOnly = true)
  public TypoCorrectionDictionaryResponse getTypoCorrectionDictionaryDetail(Long dictionaryId) {
    log.debug("오타교정 사전 상세 조회 요청: {}", dictionaryId);

    TypoCorrectionDictionary typoCorrectionDictionary =
        repository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오타교정 사전입니다: " + dictionaryId));

    return convertToResponse(typoCorrectionDictionary);
  }

  /** 오타교정 사전 수정 */
  @Transactional
  public TypoCorrectionDictionaryResponse updateTypoCorrectionDictionary(
      Long dictionaryId,
      TypoCorrectionDictionaryUpdateRequest request,
      DictionaryEnvironmentType environment) {
    log.info("오타교정 사전 수정 요청: {} - 환경: {}", dictionaryId, environment);

    TypoCorrectionDictionary existing =
        repository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오타교정 사전입니다: " + dictionaryId));

    if (request.getKeyword() != null) {
      existing.updateKeyword(request.getKeyword());
    }

    if (request.getDescription() != null) {
      existing.updateDescription(request.getDescription());
    }

    log.info(
        "오타교정 사전 수정 완료: {} (ID: {}) - 환경: {}", existing.getKeyword(), dictionaryId, environment);
    return convertToResponse(existing);
  }

  /** 오타교정 사전 삭제 */
  @Transactional
  public void deleteTypoCorrectionDictionary(
      Long dictionaryId, DictionaryEnvironmentType environment) {
    log.info("오타교정 사전 삭제 요청: {} - 환경: {}", dictionaryId, environment);

    if (!repository.existsById(dictionaryId)) {
      throw new IllegalArgumentException("존재하지 않는 오타교정 사전입니다: " + dictionaryId);
    }

    repository.deleteById(dictionaryId);
    log.info("오타교정 사전 삭제 완료: {} - 환경: {}", dictionaryId, environment);
  }

  /** 개발 환경으로 스냅샷 생성 (색인 실행 시 호출) */
  @Transactional
  public void createDevSnapshot() {
    log.info("개발 환경 오타교정 사전 스냅샷 생성 시작");

    List<TypoCorrectionDictionary> currentDictionaries = repository.findAll();
    if (currentDictionaries.isEmpty()) {
      log.warn("스냅샷으로 저장할 오타교정 사전이 없습니다.");
      return;
    }

    // 기존 개발 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    // 새로운 개발 환경 스냅샷 생성
    List<TypoCorrectionDictionarySnapshot> snapshots =
        currentDictionaries.stream()
            .map(
                dict ->
                    TypoCorrectionDictionarySnapshot.createSnapshot(
                        DictionaryEnvironmentType.DEV, dict))
            .toList();

    snapshotRepository.saveAll(snapshots);
    log.info("개발 환경 오타교정 사전 스냅샷 생성 완료: {}개", snapshots.size());
  }

  /** 개발 환경으로 현재사전 배포 (색인 시 호출) */
  @Transactional
  public void deployToDev() {
    log.info("개발 환경 오타교정 사전 배포 시작");

    // 현재사전 조회
    List<TypoCorrectionDictionary> currentDictionaries = repository.findAllByOrderByKeywordAsc();

    // 기존 개발 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    // 현재사전을 개발 환경 스냅샷으로 복사
    List<TypoCorrectionDictionarySnapshot> devSnapshots =
        currentDictionaries.stream()
            .map(
                dict ->
                    TypoCorrectionDictionarySnapshot.createSnapshot(
                        DictionaryEnvironmentType.DEV, dict))
            .toList();

    snapshotRepository.saveAll(devSnapshots);
    log.info("개발 환경 오타교정 사전 배포 완료: {}개", devSnapshots.size());
  }

  /** 운영 환경으로 스냅샷 배포 (배포 시 호출) */
  @Transactional
  public void deployToProd() {
    log.info("운영 환경 오타교정 사전 스냅샷 배포 시작");

    // 개발 환경 스냅샷 조회
    List<TypoCorrectionDictionarySnapshot> devSnapshots =
        snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(DictionaryEnvironmentType.DEV);

    // 기존 운영 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.PROD);

    // 개발 환경 스냅샷을 운영 환경으로 복사 (빈 사전도 정상 처리)
    List<TypoCorrectionDictionarySnapshot> prodSnapshots =
        devSnapshots.stream()
            .map(
                devSnapshot ->
                    TypoCorrectionDictionarySnapshot.createSnapshot(
                        DictionaryEnvironmentType.PROD,
                        TypoCorrectionDictionary.builder()
                            .id(devSnapshot.getOriginalDictionaryId())
                            .keyword(devSnapshot.getKeyword())
                            .correctedWord(devSnapshot.getCorrectedWord())
                            .description(devSnapshot.getDescription())
                            .build()))
            .toList();

    snapshotRepository.saveAll(prodSnapshots);
    log.info("운영 환경 오타교정 사전 스냅샷 배포 완료: {}개", prodSnapshots.size());
  }

  /** 정렬 조건 생성 */
  private Sort createSort(String sortBy, String sortDir, boolean isSnapshot) {
    if (sortBy == null || sortBy.trim().isEmpty()) {
      sortBy = isSnapshot ? "createdAt" : "updatedAt";
    }
    if (sortDir == null || sortDir.trim().isEmpty()) {
      sortDir = "desc";
    }

    String[] allowedFields =
        isSnapshot
            ? new String[] {"keyword", "createdAt", "updatedAt"}
            : new String[] {"keyword", "createdAt", "updatedAt"};

    boolean isValidField = false;
    for (String field : allowedFields) {
      if (field.equals(sortBy)) {
        isValidField = true;
        break;
      }
    }

    if (!isValidField) {
      log.warn("허용되지 않은 정렬 필드: {}. 기본값 {} 사용", sortBy, isSnapshot ? "createdAt" : "updatedAt");
      sortBy = isSnapshot ? "createdAt" : "updatedAt";
    }

    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return Sort.by(direction, sortBy);
  }

  /**
   * 저장된 오타 교정어 추천을 현재 사전으로 일괄 반영
   * - recommendationCount < minRecommendationCount 인 항목은 건너뜀
   * - 이미 동일 키워드가 사전에 존재하면 건너뜀
   * - deleteAfterInsert=true 이면 성공 반영된 추천은 추천 테이블에서 삭제
   */
  @Transactional
  public PromotionResult promoteRecommendationsToCurrentDictionary(
      int minRecommendationCount, boolean deleteAfterInsert) {
    List<TypoCorrectionRecommendation> recommendations =
        recommendationRepository.findAllByOrderByRecommendationCountDesc();

    int total = 0;
    int inserted = 0;
    int skippedExists = 0;
    int skippedMalformed = 0;
    int skippedBelow = 0;
    List<String> deleteIds = new ArrayList<>();

    for (TypoCorrectionRecommendation rec : recommendations) {
      total++;
      if (rec.getRecommendationCount() < minRecommendationCount) {
        skippedBelow++;
        continue;
      }

      String pair = rec.getPair();
      if (pair == null) {
        skippedMalformed++;
        continue;
      }
      String[] parts = pair.split(",", 2);
      if (parts.length != 2) {
        skippedMalformed++;
        continue;
      }
      String typo = parts[0].trim();
      String correction = parts[1].trim();
      if (typo.isEmpty() || correction.isEmpty()) {
        skippedMalformed++;
        continue;
      }

      if (repository.existsByKeyword(typo)) {
        skippedExists++;
        continue;
      }

      TypoCorrectionDictionary entity =
          TypoCorrectionDictionary.builder()
              .keyword(typo)
              .correctedWord(correction)
              .description(rec.getReason())
              .build();
      repository.save(entity);
      inserted++;
      if (deleteAfterInsert) {
        deleteIds.add(rec.getPair());
      }
    }

    int deleted = 0;
    if (deleteAfterInsert && !deleteIds.isEmpty()) {
      recommendationRepository.deleteAllByIdInBatch(deleteIds);
      deleted = deleteIds.size();
    }

    return new PromotionResult(total, inserted, skippedExists, skippedMalformed, skippedBelow, deleted);
  }

  public record PromotionResult(
      int total,
      int inserted,
      int skippedExists,
      int skippedMalformed,
      int skippedBelowMinCount,
      int deleted) {}

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

  /** Snapshot to ListResponse 변환 */
  private TypoCorrectionDictionaryListResponse toTypoCorrectionDictionaryListResponseFromSnapshot(
      TypoCorrectionDictionarySnapshot snapshot) {
    return TypoCorrectionDictionaryListResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .correctedWord(snapshot.getCorrectedWord())
        .description(snapshot.getDescription())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }
}
