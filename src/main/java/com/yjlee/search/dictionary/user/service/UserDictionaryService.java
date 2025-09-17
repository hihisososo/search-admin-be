package com.yjlee.search.dictionary.user.service;

import com.yjlee.search.common.domain.FileUploadResult;
import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.service.FileUploadService;
import com.yjlee.search.dictionary.common.enums.DictionarySortField;
import com.yjlee.search.dictionary.common.model.DictionaryData;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryListResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.mapper.UserDictionaryMapper;
import com.yjlee.search.dictionary.user.model.UserDictionary;
import com.yjlee.search.dictionary.user.repository.UserDictionaryRepository;
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
public class UserDictionaryService {

  private static final String EC2_USER_DICT_PATH =
      "/home/ec2-user/elasticsearch/config/analysis/user";

  private final UserDictionaryRepository userDictionaryRepository;
  private final FileUploadService fileUploadService;
  private final UserDictionaryMapper mapper;

  public UserDictionaryResponse create(
      UserDictionaryCreateRequest request, EnvironmentType environment) {
    UserDictionary entity =
        UserDictionary.of(request.getKeyword(), request.getDescription(), environment);
    UserDictionary saved = userDictionaryRepository.save(entity);
    return mapper.toResponse(saved);
  }

  public PageResponse<UserDictionaryListResponse> getList(
      int page,
      int size,
      String sortBy,
      String sortDir,
      String keyword,
      EnvironmentType environment) {

    Sort sort = createSort(sortBy, sortDir);
    Pageable pageable = PageRequest.of(page, size, sort);

    Page<UserDictionary> entities =
        userDictionaryRepository.findWithOptionalKeyword(environment, keyword, pageable);

    return PageResponse.from(entities.map(mapper::toListResponse));
  }

  public UserDictionaryResponse get(Long id, EnvironmentType environment) {

    UserDictionary entity =
        userDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사용자 사전을 찾을 수 없습니다: " + id));
    return mapper.toResponse(entity);
  }

  public UserDictionaryResponse update(
      Long id, UserDictionaryUpdateRequest request, EnvironmentType environment) {

    UserDictionary entity =
        userDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사용자 사전을 찾을 수 없습니다: " + id));

    updateEntity(entity, request);
    UserDictionary updated = userDictionaryRepository.save(entity);

    return mapper.toResponse(updated);
  }

  public void delete(Long id, EnvironmentType environment) {

    if (!userDictionaryRepository.existsById(id)) {
      throw new EntityNotFoundException("사용자 사전을 찾을 수 없습니다: " + id);
    }

    userDictionaryRepository.deleteById(id);
  }

  private List<UserDictionary> findByEnvironmentType(EnvironmentType environment) {
    return userDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environment);
  }

  public void preIndexing(DictionaryData data) {
    if (data == null || data.getUserWords().isEmpty()) {
      log.info("사용자 사전 데이터가 비어있음 - 빈 파일 생성");
    }

    String version = data.getVersion();
    List<UserDictionary> userWords = data.getUserWords();
    log.info("사용자 사전 메모리 기반 배포 시작 - 버전: {}, 단어 수: {}", version, userWords.size());

    try {
      // 사용자 단어 엔티티에서 키워드 추출하여 파일 내용으로 변환
      List<String> keywords = userWords.stream().map(UserDictionary::getKeyword).toList();
      String content = String.join("\n", keywords);

      // EC2에 파일 업로드
      FileUploadResult result =
          fileUploadService.uploadFile(version + ".txt", EC2_USER_DICT_PATH, content, version);

      if (!result.isSuccess()) {
        throw new RuntimeException("사용자사전 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("사용자사전 메모리 기반 배포 완료 - 버전: {}, 내용 길이: {}", version, content.length());

    } catch (Exception e) {
      log.error("사용자사전 메모리 기반 배포 실패", e);
      throw new RuntimeException("사용자사전 배포 실패: " + e.getMessage(), e);
    }
  }

  @Transactional
  public void copyToEnvironment(EnvironmentType from, EnvironmentType to) {
    deployToEnvironment(from, to);
  }

  private void deployToEnvironment(EnvironmentType from, EnvironmentType to) {
    List<UserDictionary> sourceDictionaries = findByEnvironmentType(from);

    // PROD 배포시 소스가 비어있어도 허용 (빈 사전도 유효함)
    if (sourceDictionaries.isEmpty()) {
      log.warn("{} 환경에서 {} 환경으로 배포할 사용자 사전이 없음 - 빈 사전으로 처리", from, to);
    }

    deleteByEnvironmentType(to);

    List<UserDictionary> targetDictionaries =
        sourceDictionaries.stream().map(dict -> mapper.copyWithEnvironment(dict, to)).toList();

    userDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 사용자 사전 배포 완료: {}개", to, targetDictionaries.size());
  }

  public void deleteByEnvironmentType(EnvironmentType environment) {
    userDictionaryRepository.deleteByEnvironmentType(environment);
  }

  @Transactional
  public void saveToEnvironment(List<UserDictionary> sourceData, EnvironmentType targetEnv) {
    if (sourceData == null || sourceData.isEmpty()) {
      log.info("사용자 사전 데이터가 비어있음 - {} 환경 스킵", targetEnv);
      return;
    }

    List<UserDictionary> targetDictionaries =
        sourceData.stream()
            .map(dict -> UserDictionary.of(dict.getKeyword(), dict.getDescription(), targetEnv))
            .toList();

    userDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 사용자 사전 저장 완료: {}개", targetEnv, targetDictionaries.size());
  }

  private void deployToEC2(String version) {
    log.info("사용자사전 EC2 배포 시작 - 버전: {}", version);

    try {
      String content = getDictionaryContent(EnvironmentType.DEV);

      if (content == null || content.trim().isEmpty()) {
        log.warn("사용자사전 내용이 비어있음 - 빈 파일 생성 - 버전: {}", version);
        content = "";
      }

      FileUploadResult result =
          fileUploadService.uploadFile(version + ".txt", EC2_USER_DICT_PATH, content, version);

      if (!result.isSuccess()) {
        throw new RuntimeException("사용자사전 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("사용자사전 EC2 업로드 완료 - 버전: {}, 내용 길이: {}", version, content.length());

    } catch (Exception e) {
      log.error("사용자사전 EC2 업로드 실패 - 버전: {}", version, e);
      throw new RuntimeException("사용자사전 EC2 업로드 실패", e);
    }
  }

  public String getDictionaryContent(EnvironmentType environment) {
    List<UserDictionary> dictionaries = findByEnvironmentType(environment);
    StringBuilder content = new StringBuilder();

    for (UserDictionary dict : dictionaries) {
      content.append(dict.getKeyword());
      content.append("\n");
    }

    return content.toString();
  }

  private void updateEntity(UserDictionary entity, UserDictionaryUpdateRequest request) {
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
