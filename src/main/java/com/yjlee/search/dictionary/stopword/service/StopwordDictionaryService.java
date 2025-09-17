package com.yjlee.search.dictionary.stopword.service;

import com.yjlee.search.common.domain.FileUploadResult;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.FileUploadService;
import com.yjlee.search.dictionary.common.enums.DictionarySortField;
import com.yjlee.search.dictionary.common.model.DictionaryData;
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
public class StopwordDictionaryService {

  private static final String EC2_STOPWORD_DICT_PATH =
      "/home/ec2-user/elasticsearch/config/analysis/stopword";

  private final StopwordDictionaryRepository stopwordDictionaryRepository;
  private final FileUploadService fileUploadService;
  private final StopwordDictionaryMapper mapper;

  public StopwordDictionaryResponse create(
      StopwordDictionaryCreateRequest request, EnvironmentType environment) {
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
      EnvironmentType environment) {

    Sort sort = createSort(sortBy, sortDir);
    Pageable pageable = PageRequest.of(page, size, sort);

    Page<StopwordDictionary> entities =
        stopwordDictionaryRepository.findWithOptionalKeyword(environment, keyword, pageable);

    return PageResponse.from(entities.map(mapper::toListResponse));
  }

  public StopwordDictionaryResponse get(Long id, EnvironmentType environment) {

    StopwordDictionary entity =
        stopwordDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("불용어 사전을 찾을 수 없습니다: " + id));
    return mapper.toResponse(entity);
  }

  public StopwordDictionaryResponse update(
      Long id, StopwordDictionaryUpdateRequest request, EnvironmentType environment) {

    StopwordDictionary entity =
        stopwordDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("불용어 사전을 찾을 수 없습니다: " + id));

    updateEntity(entity, request);
    StopwordDictionary updated = stopwordDictionaryRepository.save(entity);

    return mapper.toResponse(updated);
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

  public void preIndexing(DictionaryData data) {
    if (data == null || data.getStopwords().isEmpty()) {
      log.info("불용어 사전 데이터가 비어있음 - 빈 파일 생성");
    }

    String version = data.getVersion();
    List<StopwordDictionary> stopwords = data.getStopwords();
    log.info("불용어 사전 메모리 기반 배포 시작 - 버전: {}, 단어 수: {}", version, stopwords.size());

    try {
      // 불용어 엔티티에서 키워드 추출하여 파일 내용으로 변환
      List<String> keywords = stopwords.stream().map(StopwordDictionary::getKeyword).toList();
      String content = String.join("\n", keywords);

      // EC2에 파일 업로드
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

  @Transactional
  public void copyToEnvironment(EnvironmentType from, EnvironmentType to) {
    deployToEnvironment(from, to);
  }

  private void deployToEnvironment(EnvironmentType from, EnvironmentType to) {
    List<StopwordDictionary> sourceDictionaries = findByEnvironmentType(from);

    // PROD 배포시 소스가 비어있어도 허용 (빈 사전도 유효함)
    if (sourceDictionaries.isEmpty()) {
      log.warn("{} 환경에서 {} 환경으로 배포할 불용어 사전이 없음 - 빈 사전으로 처리", from, to);
    }

    deleteByEnvironmentType(to);

    List<StopwordDictionary> targetDictionaries =
        sourceDictionaries.stream().map(dict -> mapper.copyWithEnvironment(dict, to)).toList();

    stopwordDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 불용어 사전 배포 완료: {}개", to, targetDictionaries.size());
  }

  public void deleteByEnvironmentType(EnvironmentType environment) {
    stopwordDictionaryRepository.deleteByEnvironmentType(environment);
  }

  @Transactional
  public void saveToEnvironment(List<StopwordDictionary> sourceData, EnvironmentType targetEnv) {
    if (sourceData == null || sourceData.isEmpty()) {
      log.info("불용어 사전 데이터가 비어있음 - {} 환경 스킵", targetEnv);
      return;
    }

    List<StopwordDictionary> targetDictionaries =
        sourceData.stream()
            .map(dict -> StopwordDictionary.of(dict.getKeyword(), dict.getDescription(), targetEnv))
            .toList();

    stopwordDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 불용어 사전 저장 완료: {}개", targetEnv, targetDictionaries.size());
  }

  private void deployToEC2(String version) {
    log.info("불용어사전 EC2 배포 시작 - 버전: {}", version);

    try {
      String content = getDictionaryContent(EnvironmentType.DEV);

      if (content == null || content.trim().isEmpty()) {
        log.warn("불용어사전 내용이 비어있음 - 빈 파일 생성 - 버전: {}", version);
        content = "";
      }

      FileUploadResult result =
          fileUploadService.uploadFile(version + ".txt", EC2_STOPWORD_DICT_PATH, content, version);

      if (!result.isSuccess()) {
        throw new RuntimeException("불용어사전 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("불용어사전 EC2 업로드 완료 - 버전: {}, 내용 길이: {}", version, content.length());

    } catch (Exception e) {
      log.error("불용어사전 EC2 업로드 실패 - 버전: {}", version, e);
      throw new RuntimeException("불용어사전 EC2 업로드 실패", e);
    }
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
    if (request.getDescription() != null) {
      entity.updateDescription(request.getDescription());
    }
  }

  private Sort createSort(String sortBy, String sortDir) {
    String field = DictionarySortField.getValidFieldOrDefault(sortBy);
    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, field);
  }
}
