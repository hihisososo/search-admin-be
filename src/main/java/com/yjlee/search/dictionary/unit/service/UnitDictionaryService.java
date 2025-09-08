package com.yjlee.search.dictionary.unit.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.service.AbstractDictionaryService;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryCreateRequest;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryListResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryUpdateRequest;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import com.yjlee.search.dictionary.unit.repository.UnitDictionaryRepository;
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
public class UnitDictionaryService
    extends AbstractDictionaryService<
        UnitDictionary,
        UnitDictionaryCreateRequest,
        UnitDictionaryUpdateRequest,
        UnitDictionaryResponse,
        UnitDictionaryListResponse> {

  private final UnitDictionaryRepository unitDictionaryRepository;

  @Override
  protected JpaRepository<UnitDictionary, Long> getRepository() {
    return unitDictionaryRepository;
  }

  @Override
  protected String getDictionaryType() {
    return "단위";
  }

  @Override
  public String getDictionaryTypeEnum() {
    return "UNIT";
  }

  @Override
  protected List<UnitDictionary> findByEnvironmentType(DictionaryEnvironmentType environment) {
    return unitDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  @Override
  protected Page<UnitDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environment, Pageable pageable) {
    return unitDictionaryRepository.findByEnvironmentType(environment, pageable);
  }

  @Override
  protected void deleteByEnvironmentType(DictionaryEnvironmentType environment) {
    unitDictionaryRepository.deleteByEnvironmentType(environment);
  }

  @Override
  public String getDictionaryContent(DictionaryEnvironmentType environment) {
    List<UnitDictionary> dictionaries = findByEnvironmentType(environment);
    StringBuilder content = new StringBuilder();

    for (UnitDictionary dict : dictionaries) {
      content.append(dict.getKeyword()).append("\n");
    }

    return content.toString();
  }

  @Override
  protected UnitDictionary buildEntity(UnitDictionaryCreateRequest request) {
    return UnitDictionary.builder().keyword(request.getKeyword()).build();
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
  protected UnitDictionaryListResponse convertToListResponse(UnitDictionary entity) {
    return UnitDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
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
  protected UnitDictionary copyEntityWithEnvironment(
      UnitDictionary entity, DictionaryEnvironmentType environment) {
    return UnitDictionary.builder()
        .keyword(entity.getKeyword())
        .environmentType(environment)
        .build();
  }
}
