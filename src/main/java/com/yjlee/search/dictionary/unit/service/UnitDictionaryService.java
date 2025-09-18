package com.yjlee.search.dictionary.unit.service;

import com.yjlee.search.common.domain.FileUploadResult;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.FileUploadService;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryCreateRequest;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryListResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryUpdateRequest;
import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import com.yjlee.search.dictionary.unit.repository.UnitDictionaryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  public UnitDictionaryResponse create(
      UnitDictionaryCreateRequest request, EnvironmentType environment) {
    UnitDictionary entity = UnitDictionary.of(request.getKeyword(), environment);
    UnitDictionary saved = unitDictionaryRepository.save(entity);
    return UnitDictionaryResponse.from(saved);
  }

  public PageResponse<UnitDictionaryListResponse> getList(
      Pageable pageable, String keyword, EnvironmentType environment) {

    Page<UnitDictionary> entities =
        unitDictionaryRepository.findWithOptionalKeyword(environment, keyword, pageable);

    return PageResponse.from(entities.map(UnitDictionaryListResponse::from));
  }

  public UnitDictionaryResponse get(Long id, EnvironmentType environment) {

    UnitDictionary entity =
        unitDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("단위 사전을 찾을 수 없습니다: " + id));
    return UnitDictionaryResponse.from(entity);
  }

  public UnitDictionaryResponse update(
      Long id, UnitDictionaryUpdateRequest request, EnvironmentType environment) {

    UnitDictionary entity =
        unitDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("단위 사전을 찾을 수 없습니다: " + id));

    updateEntity(entity, request);
    UnitDictionary updated = unitDictionaryRepository.save(entity);

    return UnitDictionaryResponse.from(updated);
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

  public void upload(List<UnitDictionary> units, String version) {
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

  public void deleteByEnvironmentType(EnvironmentType environment) {
    unitDictionaryRepository.deleteByEnvironmentType(environment);
  }

  @Transactional
  public void saveToEnvironment(List<UnitDictionary> sourceData, EnvironmentType targetEnv) {
    // 기존 환경 데이터 삭제
    deleteByEnvironmentType(targetEnv);
    unitDictionaryRepository.flush(); // delete를 DB에 즉시 반영

    if (sourceData == null || sourceData.isEmpty()) {
      return;
    }

    List<UnitDictionary> targetDictionaries =
        sourceData.stream().map(dict -> UnitDictionary.of(dict.getKeyword(), targetEnv)).toList();

    unitDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 단위 사전 저장 완료: {}개", targetEnv, targetDictionaries.size());
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
}
