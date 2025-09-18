package com.yjlee.search.dictionary.stopword.service;

import com.yjlee.search.common.domain.FileUploadResult;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.FileUploadService;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryListResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryUpdateRequest;
import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import com.yjlee.search.dictionary.stopword.repository.StopwordDictionaryRepository;
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
public class StopwordDictionaryService {

  private static final String EC2_STOPWORD_DICT_PATH =
      "/home/ec2-user/elasticsearch/config/analysis/stopword";

  private final StopwordDictionaryRepository stopwordDictionaryRepository;
  private final FileUploadService fileUploadService;

  public StopwordDictionaryResponse create(
      StopwordDictionaryCreateRequest request, EnvironmentType environment) {
    StopwordDictionary entity = StopwordDictionary.of(request.getKeyword(), environment);
    StopwordDictionary saved = stopwordDictionaryRepository.save(entity);
    return StopwordDictionaryResponse.from(saved);
  }

  public PageResponse<StopwordDictionaryListResponse> getList(
      Pageable pageable, String keyword, EnvironmentType environment) {

    Page<StopwordDictionary> entities =
        stopwordDictionaryRepository.findWithOptionalKeyword(environment, keyword, pageable);

    return PageResponse.from(entities.map(StopwordDictionaryListResponse::from));
  }

  public StopwordDictionaryResponse get(Long id, EnvironmentType environment) {

    StopwordDictionary entity =
        stopwordDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("불용어 사전을 찾을 수 없습니다: " + id));
    return StopwordDictionaryResponse.from(entity);
  }

  public StopwordDictionaryResponse update(
      Long id, StopwordDictionaryUpdateRequest request, EnvironmentType environment) {

    StopwordDictionary entity =
        stopwordDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("불용어 사전을 찾을 수 없습니다: " + id));

    updateEntity(entity, request);
    StopwordDictionary updated = stopwordDictionaryRepository.save(entity);

    return StopwordDictionaryResponse.from(updated);
  }

  public void delete(Long id, EnvironmentType environment) {

    if (!stopwordDictionaryRepository.existsById(id)) {
      throw new EntityNotFoundException("불용어 사전을 찾을 수 없습니다: " + id);
    }

    stopwordDictionaryRepository.deleteById(id);
  }

  private List<StopwordDictionary> findByEnvironmentType(EnvironmentType environment) {
    return stopwordDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  public void upload(List<StopwordDictionary> stopwords, String version) {
    log.info("불용어 사전 메모리 기반 배포 시작 - 버전: {}, 단어 수: {}", version, stopwords.size());

    try {
      List<String> keywords = stopwords.stream().map(StopwordDictionary::getKeyword).toList();
      String content = String.join("\n", keywords);

      FileUploadResult result =
          fileUploadService.uploadFile(version + ".txt", EC2_STOPWORD_DICT_PATH, content, version);

      if (!result.isSuccess()) {
        throw new RuntimeException("불용어사전 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("불용어사전 메모리 기반 배포 완료 - 버전: {}, 내용 길이: {}", version, content.length());

    } catch (Exception e) {
      log.error("불용어사전 메모리 기반 배포 실패", e);
      throw new RuntimeException("불용어사전 배포 실패: " + e.getMessage(), e);
    }
  }

  public void deleteByEnvironmentType(EnvironmentType environment) {
    stopwordDictionaryRepository.deleteByEnvironmentType(environment);
  }

  @Transactional
  public void saveToEnvironment(List<StopwordDictionary> sourceData, EnvironmentType targetEnv) {
    deleteByEnvironmentType(targetEnv);
    stopwordDictionaryRepository.flush(); // delete를 DB에 즉시 반영

    if (sourceData == null || sourceData.isEmpty()) {
      return;
    }

    List<StopwordDictionary> targetDictionaries =
        sourceData.stream()
            .map(dict -> StopwordDictionary.of(dict.getKeyword(), targetEnv))
            .toList();

    stopwordDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 불용어 사전 저장 완료: {}개", targetEnv, targetDictionaries.size());
  }

  public String getDictionaryContent(EnvironmentType environment) {
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
  }
}
