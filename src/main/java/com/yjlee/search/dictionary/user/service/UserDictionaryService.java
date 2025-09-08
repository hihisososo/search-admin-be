package com.yjlee.search.dictionary.user.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.service.AbstractDictionaryService;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryListResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
import java.util.List;
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
        UserDictionaryCreateRequest,
        UserDictionaryUpdateRequest,
        UserDictionaryResponse,
        UserDictionaryListResponse> {

  private final UserDictionaryRepository userDictionaryRepository;

  @Override
  protected JpaRepository<UserDictionary, Long> getRepository() {
    return userDictionaryRepository;
  }

  @Override
  protected String getDictionaryType() {
    return "사용자";
  }

  @Override
  public String getDictionaryTypeEnum() {
    return "USER";
  }

  @Override
  protected List<UserDictionary> findByEnvironmentType(DictionaryEnvironmentType environment) {
    return userDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  @Override
  protected Page<UserDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environment, Pageable pageable) {
    return userDictionaryRepository.findByEnvironmentType(environment, pageable);
  }

  @Override
  protected void deleteByEnvironmentType(DictionaryEnvironmentType environment) {
    userDictionaryRepository.deleteByEnvironmentType(environment);
  }

  @Override
  public String getDictionaryContent(DictionaryEnvironmentType environment) {
    List<UserDictionary> dictionaries = findByEnvironmentType(environment);
    StringBuilder content = new StringBuilder();

    for (UserDictionary dict : dictionaries) {
      content.append(dict.getKeyword());
      if (dict.getDescription() != null && !dict.getDescription().isEmpty()) {
        content.append(" - ").append(dict.getDescription());
      }
      content.append("\n");
    }

    return content.toString();
  }

  @Override
  protected UserDictionary buildEntity(UserDictionaryCreateRequest request) {
    return UserDictionary.builder()
        .keyword(request.getKeyword())
        .description(request.getDescription())
        .build();
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
  protected UserDictionary copyEntityWithEnvironment(
      UserDictionary entity, DictionaryEnvironmentType environment) {
    return UserDictionary.builder()
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .environmentType(environment)
        .build();
  }
}
