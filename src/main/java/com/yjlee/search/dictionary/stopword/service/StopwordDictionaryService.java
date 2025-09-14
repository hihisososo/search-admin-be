package com.yjlee.search.dictionary.stopword.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.domain.FileUploadResult;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.FileUploadService;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
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

  private static final String EC2_STOPWORD_DICT_PATH =
      "/home/ec2-user/elasticsearch/config/analysis/stopword";

  private final StopwordDictionaryRepository stopwordDictionaryRepository;
  private final FileUploadService fileUploadService;
  private final StopwordDictionaryMapper mapper;
  private final IndexEnvironmentRepository indexEnvironmentRepository;

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
        (keyword != null && !keyword.trim().isEmpty())
            ? searchInRepository(environment, keyword.trim(), pageable)
            : findByEnvironmentType(environment, pageable);
    Page<StopwordDictionaryListResponse> resultPage = entities.map(mapper::toListResponse);

    return PageResponse.from(resultPage);
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

  private Page<StopwordDictionary> findByEnvironmentType(
      EnvironmentType environment, Pageable pageable) {
    return stopwordDictionaryRepository.findByEnvironmentType(environment, pageable);
  }

  @Override
  public void preIndexing() {
    deployToEnvironment(EnvironmentType.CURRENT, EnvironmentType.DEV);

    // 버전 정보 DB에서 조회
    IndexEnvironment devEnv =
        indexEnvironmentRepository
            .findByEnvironmentType(EnvironmentType.DEV)
            .orElseThrow(() -> new IllegalStateException("DEV 환경이 없습니다"));
    String version = devEnv.getVersion();
    if (version == null) {
      throw new IllegalStateException("DEV 환경에 버전이 설정되지 않았습니다");
    }

    deployToEC2(version);
  }

  @Override
  public void preDeploy() {
    deployToEnvironment(EnvironmentType.DEV, EnvironmentType.PROD);
  }

  @Override
  public void deployToTemp() {
    log.info("불용어사전 임시 환경 배포 시작");

    try {
      String content = getDictionaryContent(EnvironmentType.CURRENT);

      if (content == null || content.trim().isEmpty()) {
        log.warn("불용어사전 내용이 비어있음 - 임시 환경");
        content = "";
      }

      FileUploadResult result =
          fileUploadService.uploadFile(
              "temp-current.txt", EC2_STOPWORD_DICT_PATH, content, "temp-current");

      if (!result.isSuccess()) {
        throw new RuntimeException("불용어사전 임시 환경 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("불용어사전 임시 환경 EC2 업로드 완료 - 내용 길이: {}", content.length());

    } catch (Exception e) {
      log.error("불용어사전 임시 환경 EC2 업로드 실패", e);
      throw new RuntimeException("불용어사전 임시 환경 배포 실패", e);
    }
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

  @Override
  public void deleteByEnvironmentType(EnvironmentType environment) {
    stopwordDictionaryRepository.deleteByEnvironmentType(environment);
  }

  @Override
  public void realtimeSync(EnvironmentType environment) {
    log.info("불용어사전 실시간 동기화는 지원하지 않음 - 환경: {}", environment);
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

  private Page<StopwordDictionary> searchInRepository(
      EnvironmentType environmentType, String keyword, Pageable pageable) {
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
