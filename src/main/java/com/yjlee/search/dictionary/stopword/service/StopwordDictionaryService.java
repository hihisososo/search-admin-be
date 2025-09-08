package com.yjlee.search.dictionary.stopword.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.service.AbstractDictionaryService;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryListResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryUpdateRequest;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionaryRepository;
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
public class StopwordDictionaryService
    extends AbstractDictionaryService<
        StopwordDictionary,
        StopwordDictionaryCreateRequest,
        StopwordDictionaryUpdateRequest,
        StopwordDictionaryResponse,
        StopwordDictionaryListResponse> {

  private final StopwordDictionaryRepository stopwordDictionaryRepository;

  @Override
  protected JpaRepository<StopwordDictionary, Long> getRepository() {
    return stopwordDictionaryRepository;
  }

  @Override
  protected String getDictionaryType() {
    return "불용어";
  }

  @Override
  public String getDictionaryTypeEnum() {
    return "STOPWORD";
  }

  @Override
  protected List<StopwordDictionary> findByEnvironmentType(DictionaryEnvironmentType environment) {
    return stopwordDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  @Override
  protected Page<StopwordDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environment, Pageable pageable) {
    return stopwordDictionaryRepository.findByEnvironmentType(environment, pageable);
  }

  @Override
  protected void deleteByEnvironmentType(DictionaryEnvironmentType environment) {
    stopwordDictionaryRepository.deleteByEnvironmentType(environment);
  }

  @Override
  public String getDictionaryContent(DictionaryEnvironmentType environment) {
    List<StopwordDictionary> dictionaries = findByEnvironmentType(environment);
    StringBuilder content = new StringBuilder();

    for (StopwordDictionary dict : dictionaries) {
      content.append(dict.getKeyword());
      if (dict.getDescription() != null && !dict.getDescription().isEmpty()) {
        content.append(" - ").append(dict.getDescription());
      }
      content.append("\n");
    }

    return content.toString();
  }

  @Override
  protected StopwordDictionary buildEntity(StopwordDictionaryCreateRequest request) {
    return StopwordDictionary.builder()
        .keyword(request.getKeyword())
        .description(request.getDescription())
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
  protected StopwordDictionary copyEntityWithEnvironment(
      StopwordDictionary entity, DictionaryEnvironmentType environment) {
    return StopwordDictionary.builder()
        .keyword(entity.getKeyword())
        .description(entity.getDescription())
        .environmentType(environment)
        .build();
  }
}
