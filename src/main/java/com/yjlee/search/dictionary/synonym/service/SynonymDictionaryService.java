package com.yjlee.search.dictionary.synonym.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.service.AbstractDictionaryService;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryListResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import com.yjlee.search.dictionary.synonym.model.SynonymDictionarySnapshot;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionaryRepository;
import com.yjlee.search.dictionary.synonym.repository.SynonymDictionarySnapshotRepository;
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
public class SynonymDictionaryService
    extends AbstractDictionaryService<
        SynonymDictionary,
        SynonymDictionarySnapshot,
        SynonymDictionaryCreateRequest,
        SynonymDictionaryUpdateRequest,
        SynonymDictionaryResponse,
        SynonymDictionaryListResponse> {

  private final SynonymDictionaryRepository synonymDictionaryRepository;
  private final SynonymDictionarySnapshotRepository snapshotRepository;

  @Override
  protected JpaRepository<SynonymDictionary, Long> getRepository() {
    return synonymDictionaryRepository;
  }

  @Override
  protected JpaRepository<SynonymDictionarySnapshot, Long> getSnapshotRepository() {
    return snapshotRepository;
  }

  @Override
  protected String getDictionaryType() {
    return "동의어";
  }

  @Override
  protected SynonymDictionary buildEntity(SynonymDictionaryCreateRequest request) {
    return SynonymDictionary.builder()
        .keyword(request.getKeyword())
        .description(request.getDescription())
        .build();
  }

  @Override
  protected SynonymDictionarySnapshot createSnapshot(
      DictionaryEnvironmentType env, SynonymDictionary entity) {
    return SynonymDictionarySnapshot.builder()
        .environmentType(env)
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .build();
  }

  @Override
  protected SynonymDictionaryResponse convertToResponse(SynonymDictionary entity) {
    return SynonymDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  @Override
  protected SynonymDictionaryResponse convertToResponse(SynonymDictionarySnapshot snapshot) {
    return SynonymDictionaryResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .createdAt(snapshot.getCreatedAt())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }

  @Override
  protected SynonymDictionaryListResponse convertToListResponse(SynonymDictionary entity) {
    return SynonymDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  @Override
  protected SynonymDictionaryListResponse convertToListResponse(
      SynonymDictionarySnapshot snapshot) {
    return SynonymDictionaryListResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .createdAt(snapshot.getCreatedAt())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }

  @Override
  protected void updateEntity(SynonymDictionary entity, SynonymDictionaryUpdateRequest request) {
    if (request.getKeyword() != null) {
      entity.updateKeyword(request.getKeyword());
    }
    if (request.getDescription() != null) {
      entity.updateDescription(request.getDescription());
    }
  }

  @Override
  protected Page<SynonymDictionary> searchInRepository(String keyword, Pageable pageable) {
    return synonymDictionaryRepository.findByKeywordContainingIgnoreCase(keyword, pageable);
  }

  @Override
  protected Page<SynonymDictionarySnapshot> searchInSnapshotRepository(
      String keyword, DictionaryEnvironmentType environment, Pageable pageable) {
    return snapshotRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
        environment, keyword, pageable);
  }

  @Override
  protected SynonymDictionarySnapshot createSnapshotFromSnapshot(
      DictionaryEnvironmentType env, SynonymDictionarySnapshot snapshot) {
    return SynonymDictionarySnapshot.builder()
        .environmentType(env)
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .build();
  }

  @Override
  protected Page<SynonymDictionarySnapshot> findSnapshotsByEnvironment(
      DictionaryEnvironmentType environment, Pageable pageable) {
    return snapshotRepository.findByEnvironmentType(environment, pageable);
  }

  @Override
  protected Optional<SynonymDictionarySnapshot> findSnapshotByOriginalIdAndEnvironment(
      Long originalId, DictionaryEnvironmentType environment) {
    // 더 이상 사용되지 않음 - AbstractDictionaryService.get()에서 직접 스냅샷 ID로 조회
    return Optional.empty();
  }

  @Override
  protected List<SynonymDictionarySnapshot> findAllSnapshotsByEnvironment(
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
    log.info("개발 환경 동의어 사전 스냅샷 삭제 시작");
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);
    log.info("개발 환경 동의어 사전 스냅샷 삭제 완료");
  }
}
