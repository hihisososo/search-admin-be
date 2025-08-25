package com.yjlee.search.dictionary.synonym.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryListResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionarySnapshot;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionarySnapshotRepository;
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
public class SynonymDictionaryService {

  private final SynonymDictionaryRepository synonymDictionaryRepository;
  private final SynonymDictionarySnapshotRepository snapshotRepository;

  /** 유의어 사전 생성 */
  @Transactional
  public SynonymDictionaryResponse createSynonymDictionary(
      SynonymDictionaryCreateRequest request, DictionaryEnvironmentType environment) {
    log.info("유의어 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);

    if (environment == null || environment == DictionaryEnvironmentType.CURRENT) {
      // 현재 사전에 생성
      SynonymDictionary synonymDictionary =
          SynonymDictionary.builder()
              .keyword(request.getKeyword())
              .description(request.getDescription())
              .build();

      SynonymDictionary saved = synonymDictionaryRepository.save(synonymDictionary);
      log.info("유의어 사전 생성 완료: {} (ID: {}) - 환경: CURRENT", saved.getKeyword(), saved.getId());

      return toSynonymDictionaryResponse(saved);
    } else {
      // DEV/PROD 스냅샷에 직접 생성
      SynonymDictionarySnapshot snapshot =
          SynonymDictionarySnapshot.builder()
              .environmentType(environment)
              .keyword(request.getKeyword())
              .description(request.getDescription())
              .build();

      SynonymDictionarySnapshot saved = snapshotRepository.save(snapshot);
      log.info(
          "유의어 사전 스냅샷 생성 완료: {} (ID: {}) - 환경: {}", saved.getKeyword(), saved.getId(), environment);

      return toSynonymDictionaryResponseFromSnapshot(saved);
    }
  }

  /** 유의어 사전 목록 조회 (페이징, 검색, 정렬, 환경별) */
  @Transactional(readOnly = true)
  public PageResponse<SynonymDictionaryListResponse> getSynonymDictionaries(
      int page,
      int size,
      String search,
      String sortBy,
      String sortDir,
      DictionaryEnvironmentType environmentType) {

    log.debug(
        "유의어 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environmentType);

    // 환경 타입에 따라 현재 사전 또는 스냅샷에서 조회
    if (environmentType == null || environmentType == DictionaryEnvironmentType.CURRENT) {
      return getSynonymDictionariesFromCurrent(page, size, search, sortBy, sortDir);
    } else {
      return getSynonymDictionariesFromSnapshot(
          page, size, search, sortBy, sortDir, environmentType);
    }
  }

  /** 현재 유의어 사전 목록 조회 */
  private PageResponse<SynonymDictionaryListResponse> getSynonymDictionariesFromCurrent(
      int page, int size, String search, String sortBy, String sortDir) {

    Sort sort = createSort(sortBy, sortDir, false);
    Pageable pageable = PageRequest.of(Math.max(0, page), size, sort);

    Page<SynonymDictionary> dictionaryPage;
    if (search != null && !search.trim().isEmpty()) {
      dictionaryPage =
          synonymDictionaryRepository.findByKeywordContainingIgnoreCase(search.trim(), pageable);
    } else {
      dictionaryPage = synonymDictionaryRepository.findAll(pageable);
    }

    return PageResponse.from(dictionaryPage.map(this::toSynonymDictionaryListResponse));
  }

  /** 스냅샷에서 유의어 사전 목록 조회 */
  private PageResponse<SynonymDictionaryListResponse> getSynonymDictionariesFromSnapshot(
      int page,
      int size,
      String search,
      String sortBy,
      String sortDir,
      DictionaryEnvironmentType environmentType) {

    Sort sort = createSort(sortBy, sortDir, true);
    Pageable pageable = PageRequest.of(Math.max(0, page), size, sort);

    Page<SynonymDictionarySnapshot> snapshotPage;
    if (search != null && !search.trim().isEmpty()) {
      snapshotPage =
          snapshotRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
              environmentType, search.trim(), pageable);
    } else {
      snapshotPage =
          snapshotRepository.findByEnvironmentType(environmentType, pageable);
    }

    return PageResponse.from(
        snapshotPage.map(snapshot -> toSynonymDictionaryListResponseFromSnapshot(snapshot)));
  }

  /** 유의어 사전 상세 조회 */
  @Transactional(readOnly = true)
  public SynonymDictionaryResponse getSynonymDictionaryDetail(Long dictionaryId) {
    log.debug("유의어 사전 상세 조회 요청: {}", dictionaryId);

    SynonymDictionary synonymDictionary =
        synonymDictionaryRepository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유의어 사전입니다: " + dictionaryId));

    return toSynonymDictionaryResponse(synonymDictionary);
  }

  /** 유의어 사전 수정 */
  @Transactional
  public SynonymDictionaryResponse updateSynonymDictionary(
      Long dictionaryId,
      SynonymDictionaryUpdateRequest request,
      DictionaryEnvironmentType environment) {
    log.info("유의어 사전 수정 요청: {} - 환경: {}", dictionaryId, environment);

    if (environment == null || environment == DictionaryEnvironmentType.CURRENT) {
      // 현재 사전 수정
      SynonymDictionary existing =
          synonymDictionaryRepository
              .findById(dictionaryId)
              .orElseThrow(
                  () -> new IllegalArgumentException("존재하지 않는 유의어 사전입니다: " + dictionaryId));

      if (request.getKeyword() != null) {
        existing.updateKeyword(request.getKeyword());
      }

      if (request.getDescription() != null) {
        existing.updateDescription(request.getDescription());
      }

      SynonymDictionary updated = synonymDictionaryRepository.save(existing);
      log.info("유의어 사전 수정 완료: {}", dictionaryId);

      return toSynonymDictionaryResponse(updated);
    } else {
      // DEV/PROD 스냅샷 수정
      SynonymDictionarySnapshot existing =
          snapshotRepository
              .findById(dictionaryId)
              .orElseThrow(
                  () -> new IllegalArgumentException("존재하지 않는 유의어 사전 스냅샷입니다: " + dictionaryId));

      if (request.getKeyword() != null) {
        existing.setKeyword(request.getKeyword());
      }

      if (request.getDescription() != null) {
        existing.setDescription(request.getDescription());
      }

      SynonymDictionarySnapshot updated = snapshotRepository.save(existing);
      log.info("유의어 사전 스냅샷 수정 완료: {} - 환경: {}", dictionaryId, environment);

      return toSynonymDictionaryResponseFromSnapshot(updated);
    }
  }

  /** 유의어 사전 삭제 */
  @Transactional
  public void deleteSynonymDictionary(Long dictionaryId, DictionaryEnvironmentType environment) {
    log.info("유의어 사전 삭제 요청: {} - 환경: {}", dictionaryId, environment);

    if (environment == null || environment == DictionaryEnvironmentType.CURRENT) {
      // 현재 사전에서 삭제
      if (!synonymDictionaryRepository.existsById(dictionaryId)) {
        throw new IllegalArgumentException("존재하지 않는 유의어 사전입니다: " + dictionaryId);
      }

      synonymDictionaryRepository.deleteById(dictionaryId);
      log.info("유의어 사전 삭제 완료: {} - 환경: CURRENT", dictionaryId);
    } else {
      // DEV/PROD 스냅샷에서 삭제
      if (!snapshotRepository.existsById(dictionaryId)) {
        throw new IllegalArgumentException("존재하지 않는 유의어 사전 스냅샷입니다: " + dictionaryId);
      }

      snapshotRepository.deleteById(dictionaryId);
      log.info("유의어 사전 스냅샷 삭제 완료: {} - 환경: {}", dictionaryId, environment);
    }
  }

  /** 개발 환경으로 스냅샷 생성 (색인 실행 시 호출) */
  @Transactional
  public void createDevSnapshot() {
    log.info("개발 환경 유의어 사전 스냅샷 생성 시작");

    List<SynonymDictionary> currentDictionaries = synonymDictionaryRepository.findAll();
    if (currentDictionaries.isEmpty()) {
      log.warn("스냅샷으로 저장할 유의어 사전이 없습니다.");
      return;
    }

    // 기존 개발 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    // 새로운 개발 환경 스냅샷 생성
    List<SynonymDictionarySnapshot> snapshots =
        currentDictionaries.stream()
            .map(
                dict ->
                    SynonymDictionarySnapshot.createSnapshot(DictionaryEnvironmentType.DEV, dict))
            .toList();

    snapshotRepository.saveAll(snapshots);
    log.info("개발 환경 유의어 사전 스냅샷 생성 완료: {}개", snapshots.size());
  }

  /** 개발 환경으로 현재사전 배포 (색인 시 호출) */
  @Transactional
  public void deployToDev() {
    log.info("개발 환경 유의어 사전 배포 시작");

    // 현재사전 조회
    List<SynonymDictionary> currentDictionaries =
        synonymDictionaryRepository.findAllByOrderByKeywordAsc();

    // 기존 개발 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    // 현재사전을 개발 환경 스냅샷으로 복사
    List<SynonymDictionarySnapshot> devSnapshots =
        currentDictionaries.stream()
            .map(
                dict ->
                    SynonymDictionarySnapshot.createSnapshot(DictionaryEnvironmentType.DEV, dict))
            .toList();

    snapshotRepository.saveAll(devSnapshots);
    log.info("개발 환경 유의어 사전 배포 완료: {}개", devSnapshots.size());
  }

  /** 운영 환경으로 스냅샷 배포 (배포 시 호출) */
  @Transactional
  public void deployToProd() {
    log.info("운영 환경 유의어 사전 스냅샷 배포 시작");

    // 개발 환경 스냅샷 조회
    List<SynonymDictionarySnapshot> devSnapshots =
        snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(DictionaryEnvironmentType.DEV);

    // 기존 운영 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.PROD);

    // 개발 환경 스냅샷을 운영 환경으로 복사 (빈 사전도 정상 처리)
    List<SynonymDictionarySnapshot> prodSnapshots =
        devSnapshots.stream()
            .map(
                devSnapshot ->
                    SynonymDictionarySnapshot.createSnapshot(
                        DictionaryEnvironmentType.PROD,
                        SynonymDictionary.builder()
                            .keyword(devSnapshot.getKeyword())
                            .description(devSnapshot.getDescription())
                            .build()))
            .toList();

    snapshotRepository.saveAll(prodSnapshots);
    log.info("운영 환경 유의어 사전 스냅샷 배포 완료: {}개", prodSnapshots.size());
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

  /** Entity to Response 변환 */
  private SynonymDictionaryResponse toSynonymDictionaryResponse(SynonymDictionary entity) {
    return SynonymDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Entity to ListResponse 변환 */
  private SynonymDictionaryListResponse toSynonymDictionaryListResponse(SynonymDictionary entity) {
    return SynonymDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Snapshot to ListResponse 변환 */
  private SynonymDictionaryListResponse toSynonymDictionaryListResponseFromSnapshot(
      SynonymDictionarySnapshot snapshot) {
    return SynonymDictionaryListResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }

  /** Snapshot to Response 변환 */
  private SynonymDictionaryResponse toSynonymDictionaryResponseFromSnapshot(
      SynonymDictionarySnapshot snapshot) {
    return SynonymDictionaryResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .createdAt(snapshot.getCreatedAt())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }
}
