package com.yjlee.search.dictionary.unit.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.service.AbstractDictionaryService;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryCreateRequest;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryListResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryUpdateRequest;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import com.yjlee.search.dictionary.unit.model.UnitDictionarySnapshot;
import com.yjlee.search.dictionary.unit.repository.UnitDictionaryRepository;
import com.yjlee.search.dictionary.unit.repository.UnitDictionarySnapshotRepository;
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
public class UnitDictionaryService
    extends AbstractDictionaryService<
        UnitDictionary,
        UnitDictionarySnapshot,
        UnitDictionaryCreateRequest,
        UnitDictionaryUpdateRequest,
        UnitDictionaryResponse,
        UnitDictionaryListResponse> {

  private final UnitDictionaryRepository unitDictionaryRepository;
  private final UnitDictionarySnapshotRepository snapshotRepository;

  @Override
  protected JpaRepository<UnitDictionary, Long> getRepository() {
    return unitDictionaryRepository;
  }

  @Override
  protected JpaRepository<UnitDictionarySnapshot, Long> getSnapshotRepository() {
    return snapshotRepository;
  }

  @Override
  protected String getDictionaryType() {
    return "단위";
  }

  @Override
  protected UnitDictionary buildEntity(UnitDictionaryCreateRequest request) {
    return UnitDictionary.builder().keyword(request.getKeyword()).build();
  }

  @Override
  protected UnitDictionarySnapshot createSnapshot(
      DictionaryEnvironmentType env, UnitDictionary entity) {
    return UnitDictionarySnapshot.createSnapshot(env, entity);
  }

  @Override
  protected UnitDictionaryResponse convertToResponse(UnitDictionary entity) {
    return UnitDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  @Override
  protected UnitDictionaryResponse convertToResponse(UnitDictionarySnapshot snapshot) {
    return UnitDictionaryResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .createdAt(snapshot.getCreatedAt())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }

  @Override
  protected UnitDictionaryListResponse convertToListResponse(UnitDictionary entity) {
    return UnitDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  @Override
  protected UnitDictionaryListResponse convertToListResponse(UnitDictionarySnapshot snapshot) {
    return UnitDictionaryListResponse.builder()
        .id(snapshot.getId())
        .keyword(snapshot.getKeyword())
        .createdAt(snapshot.getCreatedAt())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }

  @Override
  protected void updateEntity(UnitDictionary entity, UnitDictionaryUpdateRequest request) {
    if (request.getKeyword() != null) {
      entity.updateKeyword(request.getKeyword());
    }
  }

  @Override
  protected Page<UnitDictionary> searchInRepository(String keyword, Pageable pageable) {
    return unitDictionaryRepository.findByKeywordContainingIgnoreCase(keyword, pageable);
  }

  @Override
  protected Page<UnitDictionarySnapshot> searchInSnapshotRepository(
      String keyword, DictionaryEnvironmentType environment, Pageable pageable) {
    return snapshotRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
        environment, keyword, pageable);
  }

  @Override
  protected UnitDictionarySnapshot createSnapshotFromSnapshot(
      DictionaryEnvironmentType env, UnitDictionarySnapshot snapshot) {
    return UnitDictionarySnapshot.builder()
        .environmentType(env)
        .keyword(snapshot.getKeyword())
        .build();
  }

  @Override
  protected Page<UnitDictionarySnapshot> findSnapshotsByEnvironment(
      DictionaryEnvironmentType environment, Pageable pageable) {
    return snapshotRepository.findByEnvironmentType(environment, pageable);
  }

  @Override
  protected Optional<UnitDictionarySnapshot> findSnapshotByOriginalIdAndEnvironment(
      Long originalId, DictionaryEnvironmentType environment) {
    return Optional.empty();
  }

  @Override
  protected List<UnitDictionarySnapshot> findAllSnapshotsByEnvironment(
      DictionaryEnvironmentType environment) {
    return snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  @Override
  protected void deleteSnapshotsByEnvironment(DictionaryEnvironmentType environment) {
    snapshotRepository.deleteByEnvironmentType(environment);
  }

  @Transactional
  public void deleteDevSnapshots() {
    log.info("개발 환경 단위 사전 스냅샷 삭제 시작");
    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);
    log.info("개발 환경 단위 사전 스냅샷 삭제 완료");
  }
}
