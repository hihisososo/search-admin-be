package com.yjlee.search.dictionary.user.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.constant.DeploymentConstants;
import com.yjlee.search.deployment.service.EC2DeploymentService;
import com.yjlee.search.dictionary.common.enums.DictionarySortField;
import com.yjlee.search.dictionary.common.service.DictionaryService;
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
public class UserDictionaryService implements DictionaryService {

  private final UserDictionaryRepository userDictionaryRepository;
  private final EC2DeploymentService ec2DeploymentService;
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
        (keyword != null && !keyword.trim().isEmpty())
            ? searchInRepository(environment, keyword.trim(), pageable)
            : findByEnvironmentType(environment, pageable);
    Page<UserDictionaryListResponse> resultPage = entities.map(mapper::toListResponse);

    return PageResponse.from(resultPage);
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

  private Page<UserDictionary> findByEnvironmentType(
      EnvironmentType environment, Pageable pageable) {
    return userDictionaryRepository.findByEnvironmentType(environment, pageable);
  }

  @Override
  public void deployToDev(String version) {
    deployToEnvironment(EnvironmentType.CURRENT, EnvironmentType.DEV);
    deployToEC2(version);
  }

  @Override
  public void deployToProd() {
    deployToEnvironment(EnvironmentType.DEV, EnvironmentType.PROD);
  }

  @Override
  public void deployToTemp() {
    log.info("사용자사전 임시 환경 배포 시작");

    try {
      String content = getDictionaryContent(EnvironmentType.CURRENT);

      if (content == null || content.trim().isEmpty()) {
        log.warn("사용자사전 내용이 비어있음 - 임시 환경");
        return;
      }

      EC2DeploymentService.EC2DeploymentResult result =
          ec2DeploymentService.deployFile(
              "temp-current.txt", DeploymentConstants.EC2Paths.USER_DICT, content, "temp-current");

      if (!result.isSuccess()) {
        throw new RuntimeException("사용자사전 임시 환경 EC2 업로드 실패: " + result.getMessage());
      }

      log.info("사용자사전 임시 환경 EC2 업로드 완료 - 내용 길이: {}", content.length());

    } catch (Exception e) {
      log.error("사용자사전 임시 환경 EC2 업로드 실패", e);
      throw new RuntimeException("사용자사전 임시 환경 배포 실패", e);
    }
  }

  private void deployToEnvironment(EnvironmentType from, EnvironmentType to) {
    List<UserDictionary> sourceDictionaries = findByEnvironmentType(from);

    if (sourceDictionaries.isEmpty() && to == EnvironmentType.PROD) {
      throw new IllegalStateException("개발 환경에 배포된 사용자 사전이 없습니다.");
    }

    deleteByEnvironmentType(to);

    List<UserDictionary> targetDictionaries =
        sourceDictionaries.stream().map(dict -> mapper.copyWithEnvironment(dict, to)).toList();

    userDictionaryRepository.saveAll(targetDictionaries);
    log.info("{} 환경 사용자 사전 배포 완료: {}개", to, targetDictionaries.size());
  }

  @Override
  public void deleteByEnvironmentType(EnvironmentType environment) {
    userDictionaryRepository.deleteByEnvironmentType(environment);
  }

  public void deployToEC2(String version) {
    log.info("사용자사전 EC2 배포 시작 - 버전: {}", version);

    try {
      String content = getDictionaryContent(EnvironmentType.DEV);

      if (content == null || content.trim().isEmpty()) {
        log.warn("사용자사전 내용이 비어있음 - 버전: {}", version);
        return;
      }

      EC2DeploymentService.EC2DeploymentResult result =
          ec2DeploymentService.deployFile(
              version + ".txt", DeploymentConstants.EC2Paths.USER_DICT, content, version);

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

  private Page<UserDictionary> searchInRepository(
      EnvironmentType environmentType, String keyword, Pageable pageable) {
    return userDictionaryRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
        environmentType, keyword, pageable);
  }

  private Sort createSort(String sortBy, String sortDir) {
    String field = DictionarySortField.getValidFieldOrDefault(sortBy);
    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, field);
  }
}
