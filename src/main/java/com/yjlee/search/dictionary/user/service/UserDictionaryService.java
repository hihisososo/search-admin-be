package com.yjlee.search.dictionary.user.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.service.AbstractDictionaryService;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryListResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextRequest;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextResponse;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import com.yjlee.search.dictionary.user.model.UserDictionarySnapshot;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
import com.yjlee.search.dictionary.user.repository.UserDictionarySnapshotRepository;
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
public class UserDictionaryService
    extends AbstractDictionaryService<
        UserDictionary,
        UserDictionarySnapshot,
        UserDictionaryCreateRequest,
        UserDictionaryUpdateRequest,
        UserDictionaryResponse,
        UserDictionaryListResponse> {

  private final UserDictionaryRepository userDictionaryRepository;
  private final UserDictionarySnapshotRepository snapshotRepository;
  private final ElasticsearchAnalyzeService elasticsearchAnalyzeService;

  @Override
  protected JpaRepository<UserDictionary, Long> getRepository() {
    return userDictionaryRepository;
  }

  @Override
  protected JpaRepository<UserDictionarySnapshot, Long> getSnapshotRepository() {
    return snapshotRepository;
  }

  @Override
  protected String getDictionaryType() {
    return "사용자";
  }

  @Override
  protected UserDictionary buildEntity(UserDictionaryCreateRequest request) {
    return UserDictionary.builder()
        .keyword(request.getKeyword())
        .description(request.getDescription())
        .build();
  }

  @Override
  protected UserDictionarySnapshot createSnapshot(
      DictionaryEnvironmentType env, UserDictionary entity) {
    return UserDictionarySnapshot.createSnapshot(env, entity);
  }

  @Override
  protected UserDictionaryResponse convertToResponse(UserDictionary entity) {
    return UserDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  @Override
  protected UserDictionaryResponse convertToResponse(UserDictionarySnapshot snapshot) {
    return UserDictionaryResponse.builder()
        .id(snapshot.getOriginalDictionaryId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .createdAt(snapshot.getCreatedAt())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }

  @Override
  protected UserDictionaryListResponse convertToListResponse(UserDictionary entity) {
    return UserDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  @Override
  protected UserDictionaryListResponse convertToListResponse(UserDictionarySnapshot snapshot) {
    return UserDictionaryListResponse.builder()
        .id(snapshot.getOriginalDictionaryId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .createdAt(snapshot.getCreatedAt())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }

  @Override
  protected void updateEntity(UserDictionary entity, UserDictionaryUpdateRequest request) {
    if (request.getKeyword() != null) {
      entity.updateKeyword(request.getKeyword());
    }
    if (request.getDescription() != null) {
      entity.updateDescription(request.getDescription());
    }
  }

  @Override
  protected Page<UserDictionary> searchInRepository(String keyword, Pageable pageable) {
    return userDictionaryRepository.findByKeywordContainingIgnoreCase(keyword, pageable);
  }

  @Override
  protected Page<UserDictionarySnapshot> searchInSnapshotRepository(
      String keyword, DictionaryEnvironmentType environment, Pageable pageable) {
    return snapshotRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
        environment, keyword, pageable);
  }

  @Override
  protected UserDictionarySnapshot createSnapshotFromSnapshot(
      DictionaryEnvironmentType env, UserDictionarySnapshot snapshot) {
    return UserDictionarySnapshot.builder()
        .environmentType(env)
        .originalDictionaryId(snapshot.getOriginalDictionaryId())
        .keyword(snapshot.getKeyword())
        .description(snapshot.getDescription())
        .build();
  }

  @Override
  protected Page<UserDictionarySnapshot> findSnapshotsByEnvironment(
      DictionaryEnvironmentType environment, Pageable pageable) {
    return snapshotRepository.findByEnvironmentType(environment, pageable);
  }

  @Override
  protected Optional<UserDictionarySnapshot> findSnapshotByOriginalIdAndEnvironment(
      Long originalId, DictionaryEnvironmentType environment) {
    return snapshotRepository.findByOriginalDictionaryIdAndEnvironmentType(originalId, environment);
  }

  @Override
  protected List<UserDictionarySnapshot> findAllSnapshotsByEnvironment(
      DictionaryEnvironmentType environment) {
    return snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  @Override
  protected void deleteSnapshotsByEnvironment(DictionaryEnvironmentType environment) {
    snapshotRepository.deleteByEnvironmentType(environment);
  }

  public AnalyzeTextResponse analyzeText(AnalyzeTextRequest request, DictionaryEnvironmentType environment) {
    String text = request.getText();
    log.info("형태소 분석 요청 - 텍스트: {}, 환경: {}", text, environment);
    
    List<AnalyzeTextResponse.TokenInfo> tokens = elasticsearchAnalyzeService.analyzeText(text, environment);
    
    return AnalyzeTextResponse.builder()
        .environment(environment.name())
        .originalText(text)
        .tokens(tokens)
        .build();
  }
}
