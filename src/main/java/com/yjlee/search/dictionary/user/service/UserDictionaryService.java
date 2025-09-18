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

  public UserDictionaryResponse create(
      UserDictionaryCreateRequest request, EnvironmentType environment) {
    UserDictionary entity =
        UserDictionary.of(request.getKeyword(), environment);
    UserDictionary saved = userDictionaryRepository.save(entity);
    return UserDictionaryResponse.from(saved);
  }

  public PageResponse<UserDictionaryListResponse> getList(
      Pageable pageable,
      String keyword,
      EnvironmentType environment) {

    Page<UserDictionary> entities =
        userDictionaryRepository.findWithOptionalKeyword(environment, keyword, pageable);

    return PageResponse.from(entities.map(UserDictionaryListResponse::from));
  }

  public UserDictionaryResponse get(Long id, EnvironmentType environment) {

    UserDictionary entity =
        userDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사용자 사전을 찾을 수 없습니다: " + id));
    return UserDictionaryResponse.from(entity);
  }

  public UserDictionaryResponse update(
      Long id, UserDictionaryUpdateRequest request, EnvironmentType environment) {

    UserDictionary entity =
        userDictionaryRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사용자 사전을 찾을 수 없습니다: " + id));

    updateEntity(entity, request);
    UserDictionary updated = userDictionaryRepository.save(entity);

    return UserDictionaryResponse.from(updated);
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
    String version = data.getVersion();
    List<UserDictionary> userWords = data.getUserWords();
    log.info("사용자 사전 메모리 기반 배포 시작 - 버전: {}, 단어 수: {}", version, userWords.size());

    try {
      List<String> keywords = userWords.stream().map(UserDictionary::getKeyword).toList();
      String content = String.join("\n", keywords);

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

    deleteByEnvironmentType(to);

    List<UserDictionary> targetDictionaries =
        sourceDictionaries.stream().map(dict -> UserDictionary.copyWithEnvironment(dict, to)).toList();

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
            .map(dict -> UserDictionary.of(dict.getKeyword(), targetEnv))
            .toList();

    userDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 사용자 사전 저장 완료: {}개", targetEnv, targetDictionaries.size());
  }
  
  private void updateEntity(UserDictionary entity, UserDictionaryUpdateRequest request) {
    if (request.getKeyword() != null) {
      entity.updateKeyword(request.getKeyword());
    }
  }
}
