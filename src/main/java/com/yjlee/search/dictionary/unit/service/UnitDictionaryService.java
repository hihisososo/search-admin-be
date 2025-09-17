package com.yjlee.search.dictionary.unit.service;

import com.yjlee.search.common.domain.FileUploadResult;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.FileUploadService;
import com.yjlee.search.dictionary.common.enums.DictionarySortField;
import com.yjlee.search.dictionary.common.model.DictionaryData;
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
public class UnitDictionaryService {

  private static final String EC2_UNIT_DICT_PATH =
      "/home/ec2-user/elasticsearch/config/analysis/unit";

  private final UnitDictionaryRepository unitDictionaryRepository;
  private final FileUploadService fileUploadService;
  private final UnitDictionaryMapper mapper;

  public UnitDictionaryResponse create(
      UnitDictionaryCreateRequest request, EnvironmentType environment) {
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
      EnvironmentType environment) {

    Sort sort = createSort(sortBy, sortDir);
    Pageable pageable = PageRequest.of(page, size, sort);

    Page<UnitDictionary> entities =
        unitDictionaryRepository.findWithOptionalKeyword(environment, keyword, pageable);

    return PageResponse.from(entities.map(mapper::toListResponse));
  }

  public UnitDictionaryResponse get(Long id, EnvironmentType environment) {

    UnitDictionary entity =
        unitDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("단위 사전을 찾을 수 없습니다: " + id));
    return mapper.toResponse(entity);
  }

  public UnitDictionaryResponse update(
      Long id, UnitDictionaryUpdateRequest request, EnvironmentType environment) {

    UnitDictionary entity =
        unitDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("단위 사전을 찾을 수 없습니다: " + id));

    updateEntity(entity, request);
    UnitDictionary updated = unitDictionaryRepository.save(entity);

    return mapper.toResponse(updated);
  }

  public void delete(Long id, EnvironmentType environment) {

    if (!unitDictionaryRepository.existsById(id)) {
      throw new EntityNotFoundException("단위 사전을 찾을 수 없습니다: " + id);
    }

    unitDictionaryRepository.deleteById(id);
  }

  private List<UnitDictionary> findByEnvironmentType(EnvironmentType environment) {
    return unitDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  public void preIndexing(DictionaryData data) {
    String version = data.getVersion();
    List<UnitDictionary> units = data.getUnits();
    log.info("단위 사전 배포 시작 - 버전: {}, 단어 수: {}", version, units.size());

    try {
      List<String> keywords = units.stream().map(UnitDictionary::getKeyword).toList();
      String content = String.join("\n", keywords);
      FileUploadResult result =
          fileUploadService.uploadFile(version + ".txt", EC2_UNIT_DICT_PATH, content, version);

      if (!result.isSuccess()) {
        throw new RuntimeException("단위사전 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("단위사전 배포 완료", version);
    } catch (Exception e) {
      throw new RuntimeException("단위사전 배포 실패: " + e.getMessage(), e);
    }
  }

  @Transactional
  public void copyToEnvironment(EnvironmentType from, EnvironmentType to) {
    deployToEnvironment(from, to);
  }

  private void deployToEnvironment(EnvironmentType from, EnvironmentType to) {
    List<UnitDictionary> sourceDictionaries = findByEnvironmentType(from);

    // PROD 배포시 소스가 비어있어도 허용 (빈 사전도 유효함)
    if (sourceDictionaries.isEmpty()) {
      log.warn("{} 환경에서 {} 환경으로 배포할 단위 사전이 없음 - 빈 사전으로 처리", from, to);
    }

    deleteByEnvironmentType(to);

    List<UnitDictionary> targetDictionaries =
        sourceDictionaries.stream().map(dict -> mapper.copyWithEnvironment(dict, to)).toList();

    unitDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 단위 사전 배포 완료: {}개", to, targetDictionaries.size());
  }

  public void deleteByEnvironmentType(EnvironmentType environment) {
    unitDictionaryRepository.deleteByEnvironmentType(environment);
  }

  @Transactional
  public void saveToEnvironment(List<UnitDictionary> sourceData, EnvironmentType targetEnv) {
    if (sourceData == null || sourceData.isEmpty()) {
      log.info("단위 사전 데이터가 비어있음 - {} 환경 스킵", targetEnv);
      return;
    }

    List<UnitDictionary> targetDictionaries =
        sourceData.stream().map(dict -> UnitDictionary.of(dict.getKeyword(), targetEnv)).toList();

    unitDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 단위 사전 저장 완료: {}개", targetEnv, targetDictionaries.size());
  }

  private void deployToEC2(String version) {
    log.info("단위사전 EC2 배포 시작 - 버전: {}", version);

    try {
      String content = getDictionaryContent(EnvironmentType.DEV);

      if (content == null || content.trim().isEmpty()) {
        log.warn("단위사전 내용이 비어있음 - 빈 파일 생성 - 버전: {}", version);
        content = "";
      }

      FileUploadResult result =
          fileUploadService.uploadFile(version + ".txt", EC2_UNIT_DICT_PATH, content, version);

      if (!result.isSuccess()) {
        throw new RuntimeException("단위사전 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("단위사전 EC2 업로드 완료 - 버전: {}, 내용 길이: {}", version, content.length());

    } catch (Exception e) {
      log.error("단위사전 EC2 업로드 실패 - 버전: {}", version, e);
      throw new RuntimeException("단위사전 EC2 업로드 실패", e);
    }
  }

  public String getDictionaryContent(EnvironmentType environment) {
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

  private Sort createSort(String sortBy, String sortDir) {
    String field = DictionarySortField.getValidFieldOrDefault(sortBy);
    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, field);
  }
}
