package com.yjlee.search.dictionary.user.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.deployment.service.EC2DeploymentService;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryListResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryVersionResponse;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import com.yjlee.search.dictionary.user.model.UserDictionarySnapshot;
import com.yjlee.search.dictionary.user.model.UserDictionaryVersion;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
import com.yjlee.search.dictionary.user.repository.UserDictionarySnapshotRepository;
import com.yjlee.search.dictionary.user.repository.UserDictionaryVersionRepository;
import java.time.format.DateTimeFormatter;
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
  private final UserDictionaryVersionRepository versionRepository;
  private final EC2DeploymentService ec2DeploymentService;

  /** 사용자 사전 생성 */
  @Transactional
  public UserDictionaryResponse createUserDictionary(UserDictionaryCreateRequest request) {
    log.info("사용자 사전 생성 요청: {}", request.getKeyword());

    // 엔티티 생성
    UserDictionary userDictionary =
        UserDictionary.builder()
            .keyword(request.getKeyword())
            .description(request.getDescription())
            .build();

    // DB 저장
    UserDictionary saved = userDictionaryRepository.save(userDictionary);

    log.info("사용자 사전 생성 완료: {} (ID: {})", saved.getKeyword(), saved.getId());

    // 응답 생성
    return toUserDictionaryResponse(saved);
  }

  /** 사용자 사전 목록 조회 (페이징, 검색, 정렬, 버전) */
  @Transactional(readOnly = true)
  public PageResponse<UserDictionaryListResponse> getUserDictionaries(
      int page, int size, String search, String sortBy, String sortDir, String version) {
    log.debug(
        "사용자 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, version: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        version);

    // version이 있으면 스냅샷에서 조회, 없으면 현재 사전에서 조회
    if (version != null && !version.trim().isEmpty()) {
      return getUserDictionariesFromSnapshot(page, size, search, sortBy, sortDir, version.trim());
    } else {
      return getUserDictionariesFromCurrent(page, size, search, sortBy, sortDir);
    }
  }

  /** 현재 사용자 사전 목록 조회 */
  private PageResponse<UserDictionaryListResponse> getUserDictionariesFromCurrent(
      int page, int size, String search, String sortBy, String sortDir) {

    Sort sort = createSort(sortBy, sortDir, false); // isSnapshot = false
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
      int page, int size, String search, String sortBy, String sortDir, String version) {

    // 버전 존재 여부 확인
    if (!snapshotRepository.existsByVersion(version)) {
      throw new IllegalArgumentException("존재하지 않는 배포 버전입니다: " + version);
    }

    Sort sort = createSort(sortBy, sortDir, true); // isSnapshot = true
    Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, sort);

    Page<UserDictionarySnapshot> snapshotPage;
    if (search != null && !search.trim().isEmpty()) {
      snapshotPage =
          snapshotRepository.findByVersionAndKeywordContainingIgnoreCaseOrderByKeywordAsc(
              version, search.trim(), pageable);
    } else {
      snapshotPage = snapshotRepository.findByVersionOrderByKeywordAsc(version, pageable);
    }

    return PageResponse.from(
        snapshotPage.map(snapshot -> toUserDictionaryListResponseFromSnapshot(snapshot, version)));
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
      Long dictionaryId, UserDictionaryUpdateRequest request) {
    log.info("사용자 사전 수정 요청: {}", dictionaryId);

    UserDictionary existing =
        userDictionaryRepository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자 사전입니다: " + dictionaryId));

    // 키워드 업데이트
    if (request.getKeyword() != null) {
      existing.updateKeyword(request.getKeyword());
    }

    // 설명 업데이트
    if (request.getDescription() != null) {
      existing.updateDescription(request.getDescription());
    }

    UserDictionary updated = userDictionaryRepository.save(existing);
    log.info("사용자 사전 수정 완료: {}", dictionaryId);

    return toUserDictionaryResponse(updated);
  }

  /** 사용자 사전 삭제 */
  @Transactional
  public void deleteUserDictionary(Long dictionaryId) {
    log.info("사용자 사전 삭제 요청: {}", dictionaryId);

    UserDictionary existing =
        userDictionaryRepository
            .findById(dictionaryId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자 사전입니다: " + dictionaryId));

    userDictionaryRepository.deleteById(dictionaryId);
    log.info("사용자 사전 삭제 완료: {}", dictionaryId);
  }

  /** 정렬 조건 생성 */
  private Sort createSort(String sortBy, String sortDir, boolean isSnapshot) {
    // 기본값 설정
    if (sortBy == null || sortBy.trim().isEmpty()) {
      sortBy = isSnapshot ? "deployedAt" : "updatedAt";
    }
    if (sortDir == null || sortDir.trim().isEmpty()) {
      sortDir = "desc";
    }

    // 허용된 정렬 필드 확인
    String[] allowedFields;
    if (isSnapshot) {
      allowedFields = new String[] {"keyword", "deployedAt"};
      // 스냅샷에서는 updatedAt을 deployedAt로 매핑
      if ("updatedAt".equals(sortBy)) {
        sortBy = "deployedAt";
      }
    } else {
      allowedFields = new String[] {"keyword", "createdAt", "updatedAt"};
    }

    boolean isValidField = false;
    for (String field : allowedFields) {
      if (field.equals(sortBy)) {
        isValidField = true;
        break;
      }
    }

    if (!isValidField) {
      log.warn("허용되지 않은 정렬 필드: {}. 기본값 {} 사용", sortBy, isSnapshot ? "deployedAt" : "updatedAt");
      sortBy = isSnapshot ? "deployedAt" : "updatedAt";
    }

    // 정렬 방향 설정
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
      UserDictionarySnapshot snapshot, String version) {
    return UserDictionaryListResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .updatedAt(snapshot.getDeployedAt()) // 스냅샷에서는 deployedAt를 updatedAt으로 사용
        .build();
  }

  /** 버전 생성 및 스냅샷 저장 (자동 버전명 생성) */
  @Transactional
  public UserDictionaryVersionResponse createVersion() {
    // 자동 버전명 생성
    String autoVersion = generateAutoVersionName();
    String autoDescription = "자동 생성된 사용자 사전 버전";

    log.info("사용자 사전 버전 생성 요청: {} (자동 생성)", autoVersion);

    // 현재 모든 사용자 사전 조회
    List<UserDictionary> currentDictionaries = userDictionaryRepository.findAll();
    if (currentDictionaries.isEmpty()) {
      throw new IllegalStateException("스냅샷으로 저장할 사용자 사전이 없습니다.");
    }

    // 새 버전 생성
    UserDictionaryVersion version =
        UserDictionaryVersion.builder().version(autoVersion).description(autoDescription).build();

    UserDictionaryVersion savedVersion = versionRepository.save(version);

    // 현재 사전들을 스냅샷으로 저장
    List<UserDictionarySnapshot> snapshots =
        currentDictionaries.stream()
            .map(dict -> UserDictionarySnapshot.createSnapshot(autoVersion, dict))
            .toList();

    snapshotRepository.saveAll(snapshots);

    log.info("사용자 사전 버전 생성 완료: {} (스냅샷 {}개 저장)", savedVersion.getVersion(), snapshots.size());

    // EC2에 사전 파일 배포
    deployDictionaryToEC2(autoVersion, snapshots);

    return UserDictionaryVersionResponse.builder()
        .id(savedVersion.getId())
        .version(savedVersion.getVersion())
        .snapshotCount(snapshots.size())
        .createdAt(savedVersion.getCreatedAt())
        .build();
  }

  /** 버전 목록 조회 (페이징) */
  @Transactional(readOnly = true)
  public PageResponse<UserDictionaryVersionResponse> getVersions(int page, int size) {
    log.debug("사용자 사전 버전 목록 조회 - page: {}, size: {}", page, size);

    Pageable pageable =
        PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<UserDictionaryVersion> versionPage =
        versionRepository.findAllByOrderByCreatedAtDesc(pageable);

    return PageResponse.from(versionPage.map(this::toUserDictionaryVersionResponse));
  }

  /** 자동 버전명 생성 */
  private String generateAutoVersionName() {
    String timestamp =
        java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss-SSS"));
    return "v" + timestamp;
  }

  /** EC2에 사전 파일 배포 */
  private void deployDictionaryToEC2(String version, List<UserDictionarySnapshot> snapshots) {
    try {
      // 스냅샷 데이터를 파일 형식으로 변환
      String dictionaryContent = convertSnapshotsToFileContent(snapshots);
      String fileName = String.format("user-dict-%s.txt", version);

      log.info("사용자 사전 EC2 배포 시작 - 버전: {}, 데이터 크기: {} bytes", version, dictionaryContent.length());

      // EC2DeploymentService를 사용하여 배포
      EC2DeploymentService.EC2DeploymentResult result =
          ec2DeploymentService.deployTestFile(dictionaryContent, fileName);

      if (result.isSuccess()) {
        log.info("사용자 사전 EC2 배포 성공 - 버전: {}, Command ID: {}", version, result.getCommandId());
      } else {
        log.error("사용자 사전 EC2 배포 실패 - 버전: {}, 오류: {}", version, result.getMessage());
      }

    } catch (Exception e) {
      log.error("사용자 사전 EC2 배포 실패 - 버전: {}", version, e);
      // 배포 실패해도 버전 생성은 성공으로 처리 (나중에 재배포 가능)
    }
  }

  /** 스냅샷을 파일 내용으로 변환 */
  private String convertSnapshotsToFileContent(List<UserDictionarySnapshot> snapshots) {
    return snapshots.stream()
        .map(UserDictionarySnapshot::getKeyword)
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  /** 버전 삭제 (스냅샷과 함께) */
  @Transactional
  public void deleteVersion(String version) {
    log.info("사용자 사전 버전 삭제 요청: {}", version);

    // 버전 존재 여부 확인
    UserDictionaryVersion existingVersion =
        versionRepository
            .findByVersion(version)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 버전입니다: " + version));

    // 해당 버전의 스냅샷들 먼저 삭제
    List<UserDictionarySnapshot> snapshots =
        snapshotRepository.findByVersionOrderByKeywordAsc(version);
    if (!snapshots.isEmpty()) {
      snapshotRepository.deleteAll(snapshots);
      log.info("버전 {} 스냅샷 {}개 삭제 완료", version, snapshots.size());
    }

    // 버전 삭제
    versionRepository.delete(existingVersion);

    log.info("사용자 사전 버전 삭제 완료: {}", version);
  }

  /** 버전 Entity to Response 변환 */
  private UserDictionaryVersionResponse toUserDictionaryVersionResponse(
      UserDictionaryVersion entity) {
    // 해당 버전의 스냅샷 개수 조회
    long snapshotCount = snapshotRepository.countByVersion(entity.getVersion());

    return UserDictionaryVersionResponse.builder()
        .id(entity.getId())
        .version(entity.getVersion())
        .snapshotCount((int) snapshotCount)
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
