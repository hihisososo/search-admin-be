package com.yjlee.search.dictionary.unit.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.constant.DeploymentConstants;
import com.yjlee.search.deployment.service.EC2DeploymentService;
import com.yjlee.search.dictionary.common.enums.DictionarySortField;
import com.yjlee.search.dictionary.common.service.DictionaryService;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryCreateRequest;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryListResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryUpdateRequest;
import com.yjlee.search.dictionary.unit.mapper.UnitDictionaryMapper;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import com.yjlee.search.dictionary.unit.repository.UnitDictionaryRepository;
import jakarta.persistence.EntityNotFoundException;
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
@Transactional
public class UnitDictionaryService implements DictionaryService {

  private final UnitDictionaryRepository unitDictionaryRepository;
  private final EC2DeploymentService ec2DeploymentService;
  private final UnitDictionaryMapper mapper;

  public UnitDictionaryResponse create(
      UnitDictionaryCreateRequest request, DictionaryEnvironmentType environment) {
    UnitDictionary entity = UnitDictionary.of(request.getKeyword(), environment);
    UnitDictionary saved = unitDictionaryRepository.save(entity);
    return mapper.toResponse(saved);
  }

  public PageResponse<UnitDictionaryListResponse> getList(
      int page,
      int size,
      String sortBy,
      String sortDir,
      String keyword,
      DictionaryEnvironmentType environment) {

    Sort sort = createSort(sortBy, sortDir);
    Pageable pageable = PageRequest.of(page, size, sort);

    Page<UnitDictionary> entities =
        (keyword != null && !keyword.trim().isEmpty())
            ? searchInRepository(environment, keyword.trim(), pageable)
            : findByEnvironmentType(environment, pageable);
    Page<UnitDictionaryListResponse> resultPage = entities.map(mapper::toListResponse);

    return PageResponse.from(resultPage);
  }

  public UnitDictionaryResponse get(Long id, DictionaryEnvironmentType environment) {

    UnitDictionary entity =
        unitDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("단위 사전을 찾을 수 없습니다: " + id));
    return mapper.toResponse(entity);
  }

  public UnitDictionaryResponse update(
      Long id, UnitDictionaryUpdateRequest request, DictionaryEnvironmentType environment) {

    UnitDictionary entity =
        unitDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("단위 사전을 찾을 수 없습니다: " + id));

    updateEntity(entity, request);
    UnitDictionary updated = unitDictionaryRepository.save(entity);

    return mapper.toResponse(updated);
  }

  public void delete(Long id, DictionaryEnvironmentType environment) {

    if (!unitDictionaryRepository.existsById(id)) {
      throw new EntityNotFoundException("단위 사전을 찾을 수 없습니다: " + id);
    }

    unitDictionaryRepository.deleteById(id);
  }

  private List<UnitDictionary> findByEnvironmentType(DictionaryEnvironmentType environment) {
    return unitDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  private Page<UnitDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environment, Pageable pageable) {
    return unitDictionaryRepository.findByEnvironmentType(environment, pageable);
  }

  @Override
  public void deployToDev(String version) {
    deployToEnvironment(DictionaryEnvironmentType.CURRENT, DictionaryEnvironmentType.DEV);
    deployToEC2(version);
  }

  @Override
  public void deployToProd() {
    deployToEnvironment(DictionaryEnvironmentType.DEV, DictionaryEnvironmentType.PROD);
  }

  private void deployToEnvironment(DictionaryEnvironmentType from, DictionaryEnvironmentType to) {
    List<UnitDictionary> sourceDictionaries = findByEnvironmentType(from);

    if (sourceDictionaries.isEmpty() && to == DictionaryEnvironmentType.PROD) {
      throw new IllegalStateException("개발 환경에 배포된 단위 사전이 없습니다.");
    }

    deleteByEnvironmentType(to);

    List<UnitDictionary> targetDictionaries =
        sourceDictionaries.stream().map(dict -> mapper.copyWithEnvironment(dict, to)).toList();

    unitDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 단위 사전 배포 완료: {}개", to, targetDictionaries.size());
  }

  @Override
  public void deleteByEnvironmentType(DictionaryEnvironmentType environment) {
    unitDictionaryRepository.deleteByEnvironmentType(environment);
  }

  public void deployToEC2(String version) {
    log.info("단위사전 EC2 배포 시작 - 버전: {}", version);

    try {
      String content = getDictionaryContent(DictionaryEnvironmentType.DEV);

      if (content == null || content.trim().isEmpty()) {
        log.warn("단위사전 내용이 비어있음 - 버전: {}", version);
        return;
      }

      EC2DeploymentService.EC2DeploymentResult result =
          ec2DeploymentService.deployFile(
              version + ".txt", DeploymentConstants.EC2Paths.UNIT_DICT, content, version);

      if (!result.isSuccess()) {
        throw new RuntimeException("단위사전 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("단위사전 EC2 업로드 완료 - 버전: {}, 내용 길이: {}", version, content.length());

    } catch (Exception e) {
      log.error("단위사전 EC2 업로드 실패 - 버전: {}", version, e);
      throw new RuntimeException("단위사전 EC2 업로드 실패", e);
    }
  }

  private String getDictionaryContent(DictionaryEnvironmentType environment) {
    List<UnitDictionary> dictionaries = findByEnvironmentType(environment);
    StringBuilder content = new StringBuilder();

    for (UnitDictionary dict : dictionaries) {
      content.append(dict.getKeyword()).append("\n");
    }

    return content.toString();
  }

  private void updateEntity(UnitDictionary entity, UnitDictionaryUpdateRequest request) {
    if (request.getKeyword() != null) {
      entity.updateKeyword(request.getKeyword());
    }
  }

  private Page<UnitDictionary> searchInRepository(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable) {
    return unitDictionaryRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
        environmentType, keyword, pageable);
  }

  private Sort createSort(String sortBy, String sortDir) {
    String field = DictionarySortField.getValidFieldOrDefault(sortBy);
    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, field);
  }
}
