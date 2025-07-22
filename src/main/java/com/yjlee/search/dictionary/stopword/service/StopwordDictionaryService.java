package com.yjlee.search.dictionary.stopword.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryListResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryUpdateRequest;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionarySnapshot;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionaryRepository;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionarySnapshotRepository;
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
public class StopwordDictionaryService {

  private final StopwordDictionaryRepository stopwordDictionaryRepository;
  private final StopwordDictionarySnapshotRepository snapshotRepository;

  /** 불용어 사전 생성 */
  @Transactional
  public StopwordDictionaryResponse createStopwordDictionary(
      StopwordDictionaryCreateRequest request, DictionaryEnvironmentType environment) {
    log.info("불용어 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);

    StopwordDictionary dictionary =
        StopwordDictionary.builder()
            .keyword(request.getKeyword())
            .description(request.getDescription())
            .build();

    StopwordDictionary saved = stopwordDictionaryRepository.save(dictionary);
    log.info("불용어 사전 생성 완료: {} (ID: {}) - 환경: {}", saved.getKeyword(), saved.getId(), environment);

    return toStopwordDictionaryResponse(saved);
  }

  /** 불용어 사전 목록 조회 (페이징, 검색, 정렬, 환경별) */
  @Transactional(readOnly = true)
  public PageResponse<StopwordDictionaryListResponse> getStopwordDictionaries(
      int page,
      int size,
      String search,
      String sortBy,
      String sortDir,
      DictionaryEnvironmentType environmentType) {

    log.debug(
        "불용어 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environmentType);

    // 환경 타입에 따라 현재 사전 또는 스냅샷에서 조회
    if (environmentType == null || environmentType == DictionaryEnvironmentType.CURRENT) {
      return getStopwordDictionariesFromCurrent(page, size, search, sortBy, sortDir);
    } else {
      return getStopwordDictionariesFromSnapshot(
          page, size, search, sortBy, sortDir, environmentType);
    }
  }

  /** 현재 불용어 사전 목록 조회 */
  private PageResponse<StopwordDictionaryListResponse> getStopwordDictionariesFromCurrent(
      int page, int size, String search, String sortBy, String sortDir) {

    Sort sort = createSort(sortBy, sortDir, false);
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, sort);

    Page<StopwordDictionary> dictionaryPage;
    if (search != null && !search.trim().isEmpty()) {
      dictionaryPage =
          stopwordDictionaryRepository.findByKeywordContainingIgnoreCase(search.trim(), pageable);
    } else {
      dictionaryPage = stopwordDictionaryRepository.findAll(pageable);
    }

    return PageResponse.from(dictionaryPage.map(this::toStopwordDictionaryListResponse));
  }

  /** 스냅샷에서 불용어 사전 목록 조회 */
  private PageResponse<StopwordDictionaryListResponse> getStopwordDictionariesFromSnapshot(
      int page,
      int size,
      String search,
      String sortBy,
      String sortDir,
      DictionaryEnvironmentType environmentType) {

    Sort sort = createSort(sortBy, sortDir, true);
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, sort);

    Page<StopwordDictionarySnapshot> snapshotPage;
    if (search != null && !search.trim().isEmpty()) {
      snapshotPage =
          snapshotRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCaseOrderByKeywordAsc(
              environmentType, search.trim(), pageable);
    } else {
      snapshotPage =
          snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(environmentType, pageable);
    }

    return PageResponse.from(
        snapshotPage.map(snapshot -> toStopwordDictionaryListResponseFromSnapshot(snapshot)));
  }

  /** 불용어 사전 상세 조회 */
  @Transactional(readOnly = true)
  public StopwordDictionaryResponse getStopwordDictionaryDetail(Long dictionaryId) {
    log.debug("불용어 사전 상세 조회 요청: {}", dictionaryId);

    StopwordDictionary stopwordDictionary =
        stopwordDictionaryRepository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 불용어 사전입니다: " + dictionaryId));

    return toStopwordDictionaryResponse(stopwordDictionary);
  }

  /** 불용어 사전 수정 */
  @Transactional
  public StopwordDictionaryResponse updateStopwordDictionary(
      Long dictionaryId,
      StopwordDictionaryUpdateRequest request,
      DictionaryEnvironmentType environment) {
    log.info("불용어 사전 수정 요청: {} - 환경: {}", dictionaryId, environment);

    StopwordDictionary existing =
        stopwordDictionaryRepository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 불용어 사전입니다: " + dictionaryId));

    if (request.getKeyword() != null) {
      existing.updateKeyword(request.getKeyword());
    }

    if (request.getDescription() != null) {
      existing.updateDescription(request.getDescription());
    }

    StopwordDictionary updated = stopwordDictionaryRepository.save(existing);
    log.info("불용어 사전 수정 완료: {} (ID: {}) - 환경: {}", updated.getKeyword(), dictionaryId, environment);

    return toStopwordDictionaryResponse(updated);
  }

  /** 불용어 사전 삭제 */
  @Transactional
  public void deleteStopwordDictionary(Long dictionaryId, DictionaryEnvironmentType environment) {
    log.info("불용어 사전 삭제 요청: {} - 환경: {}", dictionaryId, environment);

    StopwordDictionary existing =
        stopwordDictionaryRepository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 불용어 사전입니다: " + dictionaryId));

    stopwordDictionaryRepository.deleteById(dictionaryId);
    log.info("불용어 사전 삭제 완료: {} - 환경: {}", dictionaryId, environment);
  }

  /** 개발 환경으로 스냅샷 생성 (색인 실행 시 호출) */
  @Transactional
  public void createDevSnapshot() {
    log.info("개발 환경 불용어 사전 스냅샷 생성 시작");

    List<StopwordDictionary> currentDictionaries = stopwordDictionaryRepository.findAll();
    if (currentDictionaries.isEmpty()) {
      log.warn("스냅샷으로 저장할 불용어 사전이 없습니다.");
      return;
    }

    // 기존 개발 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    // 새로운 개발 환경 스냅샷 생성
    List<StopwordDictionarySnapshot> snapshots =
        currentDictionaries.stream()
            .map(
                dict ->
                    StopwordDictionarySnapshot.createSnapshot(DictionaryEnvironmentType.DEV, dict))
            .toList();

    snapshotRepository.saveAll(snapshots);
    log.info("개발 환경 불용어 사전 스냅샷 생성 완료: {}개", snapshots.size());
  }

  /** 개발 환경으로 현재사전 배포 (색인 시 호출) */
  @Transactional
  public void deployToDev() {
    log.info("개발 환경 불용어 사전 배포 시작");

    // 현재사전 조회
    List<StopwordDictionary> currentDictionaries =
        stopwordDictionaryRepository.findAllByOrderByKeywordAsc();

    // 기존 개발 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    // 현재사전을 개발 환경 스냅샷으로 복사
    List<StopwordDictionarySnapshot> devSnapshots =
        currentDictionaries.stream()
            .map(
                dict ->
                    StopwordDictionarySnapshot.createSnapshot(DictionaryEnvironmentType.DEV, dict))
            .toList();

    snapshotRepository.saveAll(devSnapshots);
    log.info("개발 환경 불용어 사전 배포 완료: {}개", devSnapshots.size());
  }

  /** 운영 환경으로 스냅샷 배포 (배포 시 호출) */
  @Transactional
  public void deployToProd() {
    log.info("운영 환경 불용어 사전 스냅샷 배포 시작");

    // 개발 환경 스냅샷 조회
    List<StopwordDictionarySnapshot> devSnapshots =
        snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(DictionaryEnvironmentType.DEV);

    // 기존 운영 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.PROD);

    // 개발 환경 스냅샷을 운영 환경으로 복사 (빈 사전도 정상 처리)
    List<StopwordDictionarySnapshot> prodSnapshots =
        devSnapshots.stream()
            .map(
                devSnapshot ->
                    StopwordDictionarySnapshot.createSnapshot(
                        DictionaryEnvironmentType.PROD,
                        StopwordDictionary.builder()
                            .id(devSnapshot.getOriginalDictionaryId())
                            .keyword(devSnapshot.getKeyword())
                            .description(devSnapshot.getDescription())
                            .build()))
            .toList();

    snapshotRepository.saveAll(prodSnapshots);
    log.info("운영 환경 불용어 사전 스냅샷 배포 완료: {}개", prodSnapshots.size());
  }

  /** 개발 환경 스냅샷 조회 (색인 시 EC2 업로드용) */
  @Transactional(readOnly = true)
  public List<StopwordDictionarySnapshot> getDevSnapshots() {
    return snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(DictionaryEnvironmentType.DEV);
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
  private StopwordDictionaryResponse toStopwordDictionaryResponse(StopwordDictionary entity) {
    return StopwordDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Entity to ListResponse 변환 */
  private StopwordDictionaryListResponse toStopwordDictionaryListResponse(
      StopwordDictionary entity) {
    return StopwordDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Snapshot to ListResponse 변환 */
  private StopwordDictionaryListResponse toStopwordDictionaryListResponseFromSnapshot(
      StopwordDictionarySnapshot snapshot) {
    return StopwordDictionaryListResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }
}
