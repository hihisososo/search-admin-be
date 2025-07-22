package com.yjlee.search.dictionary.user.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryListResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import com.yjlee.search.dictionary.user.model.UserDictionarySnapshot;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
import com.yjlee.search.dictionary.user.repository.UserDictionarySnapshotRepository;
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
public class UserDictionaryService {

  private final UserDictionaryRepository userDictionaryRepository;
  private final UserDictionarySnapshotRepository snapshotRepository;

  /** 사용자 사전 생성 */
  @Transactional
  public UserDictionaryResponse createUserDictionary(
      UserDictionaryCreateRequest request, DictionaryEnvironmentType environment) {
    log.info("사용자 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);

    UserDictionary userDictionary =
        UserDictionary.builder()
            .keyword(request.getKeyword())
            .description(request.getDescription())
            .build();

    UserDictionary saved = userDictionaryRepository.save(userDictionary);
    log.info("사용자 사전 생성 완료: {} (ID: {}) - 환경: {}", saved.getKeyword(), saved.getId(), environment);

    return toUserDictionaryResponse(saved);
  }

  /** 사용자 사전 목록 조회 (페이징, 검색, 정렬, 환경별) */
  @Transactional(readOnly = true)
  public PageResponse<UserDictionaryListResponse> getUserDictionaries(
      int page,
      int size,
      String search,
      String sortBy,
      String sortDir,
      DictionaryEnvironmentType environmentType) {

    log.debug(
        "사용자 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environmentType);

    // 환경 타입에 따라 현재 사전 또는 스냅샷에서 조회
    if (environmentType == null || environmentType == DictionaryEnvironmentType.CURRENT) {
      return getUserDictionariesFromCurrent(page, size, search, sortBy, sortDir);
    } else {
      return getUserDictionariesFromSnapshot(page, size, search, sortBy, sortDir, environmentType);
    }
  }

  /** 현재 사용자 사전 목록 조회 */
  private PageResponse<UserDictionaryListResponse> getUserDictionariesFromCurrent(
      int page, int size, String search, String sortBy, String sortDir) {

    Sort sort = createSort(sortBy, sortDir, false);
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, sort);

    Page<UserDictionary> dictionaryPage;
    if (search != null && !search.trim().isEmpty()) {
      dictionaryPage =
          userDictionaryRepository.findByKeywordContainingIgnoreCase(search.trim(), pageable);
    } else {
      dictionaryPage = userDictionaryRepository.findAll(pageable);
    }

    return PageResponse.from(dictionaryPage.map(this::toUserDictionaryListResponse));
  }

  /** 스냅샷에서 사용자 사전 목록 조회 */
  private PageResponse<UserDictionaryListResponse> getUserDictionariesFromSnapshot(
      int page,
      int size,
      String search,
      String sortBy,
      String sortDir,
      DictionaryEnvironmentType environmentType) {

    Sort sort = createSort(sortBy, sortDir, true);
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, sort);

    Page<UserDictionarySnapshot> snapshotPage;
    if (search != null && !search.trim().isEmpty()) {
      snapshotPage =
          snapshotRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCaseOrderByKeywordAsc(
              environmentType, search.trim(), pageable);
    } else {
      snapshotPage =
          snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(environmentType, pageable);
    }

    return PageResponse.from(
        snapshotPage.map(snapshot -> toUserDictionaryListResponseFromSnapshot(snapshot)));
  }

  /** 사용자 사전 상세 조회 */
  @Transactional(readOnly = true)
  public UserDictionaryResponse getUserDictionaryDetail(Long dictionaryId) {
    log.debug("사용자 사전 상세 조회 요청: {}", dictionaryId);

    UserDictionary userDictionary =
        userDictionaryRepository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자 사전입니다: " + dictionaryId));

    return toUserDictionaryResponse(userDictionary);
  }

  /** 사용자 사전 수정 */
  @Transactional
  public UserDictionaryResponse updateUserDictionary(
      Long dictionaryId,
      UserDictionaryUpdateRequest request,
      DictionaryEnvironmentType environment) {
    log.info("사용자 사전 수정 요청: {} - 환경: {}", dictionaryId, environment);

    UserDictionary existing =
        userDictionaryRepository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자 사전입니다: " + dictionaryId));

    if (request.getKeyword() != null) {
      existing.updateKeyword(request.getKeyword());
    }

    if (request.getDescription() != null) {
      existing.updateDescription(request.getDescription());
    }

    log.info(
        "사용자 사전 수정 완료: {} (ID: {}) - 환경: {}", existing.getKeyword(), dictionaryId, environment);
    return toUserDictionaryResponse(existing);
  }

  /** 사용자 사전 삭제 */
  @Transactional
  public void deleteUserDictionary(Long dictionaryId, DictionaryEnvironmentType environment) {
    log.info("사용자 사전 삭제 요청: {} - 환경: {}", dictionaryId, environment);

    if (!userDictionaryRepository.existsById(dictionaryId)) {
      throw new IllegalArgumentException("존재하지 않는 사용자 사전입니다: " + dictionaryId);
    }

    userDictionaryRepository.deleteById(dictionaryId);
    log.info("사용자 사전 삭제 완료: {} - 환경: {}", dictionaryId, environment);
  }

  /** 개발 환경으로 스냅샷 생성 (색인 실행 시 호출) */
  @Transactional
  public void createDevSnapshot() {
    log.info("개발 환경 사용자 사전 스냅샷 생성 시작");

    List<UserDictionary> currentDictionaries = userDictionaryRepository.findAll();
    if (currentDictionaries.isEmpty()) {
      log.warn("스냅샷으로 저장할 사용자 사전이 없습니다.");
      return;
    }

    // 기존 개발 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    // 새로운 개발 환경 스냅샷 생성
    List<UserDictionarySnapshot> snapshots =
        currentDictionaries.stream()
            .map(dict -> UserDictionarySnapshot.createSnapshot(DictionaryEnvironmentType.DEV, dict))
            .toList();

    snapshotRepository.saveAll(snapshots);
    log.info("개발 환경 사용자 사전 스냅샷 생성 완료: {}개", snapshots.size());
  }

  /** 개발 환경으로 현재사전 배포 (색인 시 호출) */
  @Transactional
  public void deployToDev() {
    log.info("개발 환경 사용자 사전 배포 시작");

    // 현재사전 조회
    List<UserDictionary> currentDictionaries =
        userDictionaryRepository.findAllByOrderByKeywordAsc();

    // 기존 개발 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    // 현재사전을 개발 환경 스냅샷으로 복사
    List<UserDictionarySnapshot> devSnapshots =
        currentDictionaries.stream()
            .map(dict -> UserDictionarySnapshot.createSnapshot(DictionaryEnvironmentType.DEV, dict))
            .toList();

    snapshotRepository.saveAll(devSnapshots);
    log.info("개발 환경 사용자 사전 배포 완료: {}개", devSnapshots.size());
  }

  /** 운영 환경으로 스냅샷 배포 (배포 시 호출) */
  @Transactional
  public void deployToProd() {
    log.info("운영 환경 사용자 사전 스냅샷 배포 시작");

    // 개발 환경 스냅샷 조회
    List<UserDictionarySnapshot> devSnapshots =
        snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(DictionaryEnvironmentType.DEV);

    // 기존 운영 환경 스냅샷 삭제
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.PROD);

    // 개발 환경 스냅샷을 운영 환경으로 복사 (빈 사전도 정상 처리)
    List<UserDictionarySnapshot> prodSnapshots =
        devSnapshots.stream()
            .map(
                devSnapshot ->
                    UserDictionarySnapshot.createSnapshot(
                        DictionaryEnvironmentType.PROD,
                        UserDictionary.builder()
                            .id(devSnapshot.getOriginalDictionaryId())
                            .keyword(devSnapshot.getKeyword())
                            .description(devSnapshot.getDescription())
                            .build()))
            .toList();

    snapshotRepository.saveAll(prodSnapshots);
    log.info("운영 환경 사용자 사전 스냅샷 배포 완료: {}개", prodSnapshots.size());
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
  private UserDictionaryResponse toUserDictionaryResponse(UserDictionary entity) {
    return UserDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Entity to ListResponse 변환 */
  private UserDictionaryListResponse toUserDictionaryListResponse(UserDictionary entity) {
    return UserDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Snapshot to ListResponse 변환 */
  private UserDictionaryListResponse toUserDictionaryListResponseFromSnapshot(
      UserDictionarySnapshot snapshot) {
    return UserDictionaryListResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }
}
