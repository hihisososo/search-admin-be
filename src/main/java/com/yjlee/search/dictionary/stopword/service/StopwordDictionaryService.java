package com.yjlee.search.dictionary.stopword.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.constant.DeploymentConstants;
import com.yjlee.search.deployment.service.EC2DeploymentService;
import com.yjlee.search.dictionary.common.enums.DictionarySortField;
import com.yjlee.search.dictionary.common.service.DictionaryService;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryListResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryUpdateRequest;
import com.yjlee.search.dictionary.stopword.mapper.StopwordDictionaryMapper;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionaryRepository;
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
public class StopwordDictionaryService implements DictionaryService {

  private final StopwordDictionaryRepository stopwordDictionaryRepository;
  private final EC2DeploymentService ec2DeploymentService;
  private final StopwordDictionaryMapper mapper;

  public StopwordDictionaryResponse create(
      StopwordDictionaryCreateRequest request, DictionaryEnvironmentType environment) {
    StopwordDictionary entity =
        StopwordDictionary.of(request.getKeyword(), request.getDescription(), environment);
    StopwordDictionary saved = stopwordDictionaryRepository.save(entity);
    return mapper.toResponse(saved);
  }

  public PageResponse<StopwordDictionaryListResponse> getList(
      int page,
      int size,
      String sortBy,
      String sortDir,
      String keyword,
      DictionaryEnvironmentType environment) {

    Sort sort = createSort(sortBy, sortDir);
    Pageable pageable = PageRequest.of(page, size, sort);

    Page<StopwordDictionary> entities =
        (keyword != null && !keyword.trim().isEmpty())
            ? searchInRepository(environment, keyword.trim(), pageable)
            : findByEnvironmentType(environment, pageable);
    Page<StopwordDictionaryListResponse> resultPage = entities.map(mapper::toListResponse);

    return PageResponse.from(resultPage);
  }

  public StopwordDictionaryResponse get(Long id, DictionaryEnvironmentType environment) {

    StopwordDictionary entity =
        stopwordDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("불용어 사전을 찾을 수 없습니다: " + id));
    return mapper.toResponse(entity);
  }

  public StopwordDictionaryResponse update(
      Long id, StopwordDictionaryUpdateRequest request, DictionaryEnvironmentType environment) {

    StopwordDictionary entity =
        stopwordDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("불용어 사전을 찾을 수 없습니다: " + id));

    updateEntity(entity, request);
    StopwordDictionary updated = stopwordDictionaryRepository.save(entity);

    return mapper.toResponse(updated);
  }

  public void delete(Long id, DictionaryEnvironmentType environment) {

    if (!stopwordDictionaryRepository.existsById(id)) {
      throw new EntityNotFoundException("불용어 사전을 찾을 수 없습니다: " + id);
    }

    stopwordDictionaryRepository.deleteById(id);
  }

  private List<StopwordDictionary> findByEnvironmentType(DictionaryEnvironmentType environment) {
    return stopwordDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  private Page<StopwordDictionary> findByEnvironmentType(
      DictionaryEnvironmentType environment, Pageable pageable) {
    return stopwordDictionaryRepository.findByEnvironmentType(environment, pageable);
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
    List<StopwordDictionary> sourceDictionaries = findByEnvironmentType(from);

    if (sourceDictionaries.isEmpty() && to == DictionaryEnvironmentType.PROD) {
      throw new IllegalStateException("개발 환경에 배포된 불용어 사전이 없습니다.");
    }

    deleteByEnvironmentType(to);

    List<StopwordDictionary> targetDictionaries =
        sourceDictionaries.stream().map(dict -> mapper.copyWithEnvironment(dict, to)).toList();

    stopwordDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 불용어 사전 배포 완료: {}개", to, targetDictionaries.size());
  }

  @Override
  public void deleteByEnvironmentType(DictionaryEnvironmentType environment) {
    stopwordDictionaryRepository.deleteByEnvironmentType(environment);
  }

  public void deployToEC2(String version) {
    log.info("불용어사전 EC2 배포 시작 - 버전: {}", version);

    try {
      String content = getDictionaryContent(DictionaryEnvironmentType.DEV);

      EC2DeploymentService.EC2DeploymentResult result =
          ec2DeploymentService.deployFile(
              version + ".txt", DeploymentConstants.EC2Paths.STOPWORD_DICT, content, version);

      if (!result.isSuccess()) {
        throw new RuntimeException("불용어사전 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("불용어사전 EC2 업로드 완료 - 버전: {}, 내용 길이: {}", version, content.length());

    } catch (Exception e) {
      log.error("불용어사전 EC2 업로드 실패 - 버전: {}", version, e);
      throw new RuntimeException("불용어사전 EC2 업로드 실패", e);
    }
  }

  private String getDictionaryContent(DictionaryEnvironmentType environment) {
    List<StopwordDictionary> dictionaries = findByEnvironmentType(environment);
    StringBuilder content = new StringBuilder();

    for (StopwordDictionary dict : dictionaries) {
      content.append(dict.getKeyword());
      content.append("\n");
    }

    return content.toString();
  }

  private void updateEntity(StopwordDictionary entity, StopwordDictionaryUpdateRequest request) {
    if (request.getKeyword() != null) {
      entity.updateKeyword(request.getKeyword());
    }
    if (request.getDescription() != null) {
      entity.updateDescription(request.getDescription());
    }
  }

  private Page<StopwordDictionary> searchInRepository(
      DictionaryEnvironmentType environmentType, String keyword, Pageable pageable) {
    return stopwordDictionaryRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
        environmentType, keyword, pageable);
  }

  private Sort createSort(String sortBy, String sortDir) {
    String field = DictionarySortField.getValidFieldOrDefault(sortBy);
    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, field);
  }
}
