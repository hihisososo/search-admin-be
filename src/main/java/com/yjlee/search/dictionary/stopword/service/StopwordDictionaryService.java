package com.yjlee.search.dictionary.stopword.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.service.AbstractDictionaryService;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryListResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryUpdateRequest;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionarySnapshot;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionaryRepository;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionarySnapshotRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StopwordDictionaryService
    extends AbstractDictionaryService<
        StopwordDictionary,
        StopwordDictionarySnapshot,
        StopwordDictionaryCreateRequest,
        StopwordDictionaryUpdateRequest,
        StopwordDictionaryResponse,
        StopwordDictionaryListResponse> {

  private final StopwordDictionaryRepository stopwordDictionaryRepository;
  private final StopwordDictionarySnapshotRepository snapshotRepository;

  @Override
  protected JpaRepository<StopwordDictionary, Long> getRepository() {
    return stopwordDictionaryRepository;
  }

  @Override
  protected JpaRepository<StopwordDictionarySnapshot, Long> getSnapshotRepository() {
    return snapshotRepository;
  }

  @Override
  protected String getDictionaryType() {
    return "불용어";
  }

  @Override
  protected StopwordDictionary buildEntity(StopwordDictionaryCreateRequest request) {
    return StopwordDictionary.builder()
        .keyword(request.getKeyword())
        .description(request.getDescription())
        .build();
  }

  @Override
  protected StopwordDictionarySnapshot createSnapshot(
      DictionaryEnvironmentType env, StopwordDictionary entity) {
    return StopwordDictionarySnapshot.builder()
        .environmentType(env)
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .build();
  }

  @Override
  protected StopwordDictionaryResponse convertToResponse(StopwordDictionary entity) {
    return StopwordDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  @Override
  protected StopwordDictionaryResponse convertToResponse(StopwordDictionarySnapshot snapshot) {
    return StopwordDictionaryResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .createdAt(snapshot.getCreatedAt())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }

  @Override
  protected StopwordDictionaryListResponse convertToListResponse(StopwordDictionary entity) {
    return StopwordDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  @Override
  protected StopwordDictionaryListResponse convertToListResponse(
      StopwordDictionarySnapshot snapshot) {
    return StopwordDictionaryListResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .createdAt(snapshot.getCreatedAt())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }

  @Override
  protected void updateEntity(StopwordDictionary entity, StopwordDictionaryUpdateRequest request) {
    if (request.getKeyword() != null) {
      entity.updateKeyword(request.getKeyword());
    }
    if (request.getDescription() != null) {
      entity.updateDescription(request.getDescription());
    }
  }

  @Override
  protected Page<StopwordDictionary> searchInRepository(String keyword, Pageable pageable) {
    return stopwordDictionaryRepository.findByKeywordContainingIgnoreCase(keyword, pageable);
  }

  @Override
  protected Page<StopwordDictionarySnapshot> searchInSnapshotRepository(
      String keyword, DictionaryEnvironmentType environment, Pageable pageable) {
    return snapshotRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
        environment, keyword, pageable);
  }

  @Override
  protected StopwordDictionarySnapshot createSnapshotFromSnapshot(
      DictionaryEnvironmentType env, StopwordDictionarySnapshot snapshot) {
    return StopwordDictionarySnapshot.builder()
        .environmentType(env)
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .build();
  }

  @Override
  protected Page<StopwordDictionarySnapshot> findSnapshotsByEnvironment(
      DictionaryEnvironmentType environment, Pageable pageable) {
    return snapshotRepository.findByEnvironmentType(environment, pageable);
  }

  @Override
  protected Optional<StopwordDictionarySnapshot> findSnapshotByOriginalIdAndEnvironment(
      Long originalId, DictionaryEnvironmentType environment) {
    // 더 이상 사용되지 않음 - AbstractDictionaryService.get()에서 직접 스냅샷 ID로 조회
    return Optional.empty();
  }

  @Override
  protected List<StopwordDictionarySnapshot> findAllSnapshotsByEnvironment(
      DictionaryEnvironmentType environment) {
    return snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  @Override
  protected void deleteSnapshotsByEnvironment(DictionaryEnvironmentType environment) {
    snapshotRepository.deleteByEnvironmentType(environment);
  }

  /** 개발 환경 스냅샷 삭제 */
  @Transactional
  public void deleteDevSnapshots() {
    log.info("개발 환경 불용어 사전 스냅샷 삭제 시작");
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);
    log.info("개발 환경 불용어 사전 스냅샷 삭제 완료");
  }
}
