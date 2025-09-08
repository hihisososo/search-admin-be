package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.dto.BaseDictionaryCreateRequest;
import com.yjlee.search.dictionary.common.dto.BaseDictionaryResponse;
import com.yjlee.search.dictionary.common.model.DictionaryEntity;
import jakarta.persistence.EntityNotFoundException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional
public abstract class AbstractDictionaryService<
        T extends DictionaryEntity,
        CreateReq extends BaseDictionaryCreateRequest,
        UpdateReq,
        Response extends BaseDictionaryResponse,
        ListResponse>
    implements DictionaryService {

  protected abstract JpaRepository<T, Long> getRepository();

  protected abstract String getDictionaryType();

  protected abstract List<T> findByEnvironmentType(DictionaryEnvironmentType environment);

  protected abstract Page<T> findByEnvironmentType(
      DictionaryEnvironmentType environment, Pageable pageable);

  protected abstract void deleteByEnvironmentType(DictionaryEnvironmentType environment);

  protected abstract T buildEntity(CreateReq request);

  protected abstract Response convertToResponse(T entity);

  protected abstract ListResponse convertToListResponse(T entity);

  protected abstract void updateEntity(T entity, UpdateReq request);

  protected abstract Page<T> searchInRepository(String keyword, Pageable pageable);

  public Response create(CreateReq request, DictionaryEnvironmentType environment) {
    log.info("{} 사전 생성 요청: {} - 환경: {}", getDictionaryType(), request.getKeyword(), environment);

    T entity = buildEntity(request);
    T saved = getRepository().save(entity);

    log.info(
        "{} 사전 생성 완료: {} (ID: {}) - 환경: {}",
        getDictionaryType(),
        saved.getKeyword(),
        saved.getId(),
        environment);

    return convertToResponse(saved);
  }

  public PageResponse<ListResponse> getList(
      int page,
      int size,
      String sortBy,
      String sortDir,
      String keyword,
      DictionaryEnvironmentType environment) {
    log.info(
        "{} 사전 목록 조회 - 환경: {}, 페이지: {}, 크기: {}, 정렬: {} {}, 검색어: {}",
        getDictionaryType(),
        environment,
        page,
        size,
        sortBy,
        sortDir,
        keyword);

    Sort sort = createSort(sortBy, sortDir, false);
    Pageable pageable = PageRequest.of(page, size, sort);

    Page<T> entities =
        (keyword != null && !keyword.trim().isEmpty())
            ? searchInRepository(keyword.trim(), pageable)
            : findByEnvironmentType(environment, pageable);
    Page<ListResponse> resultPage = entities.map(this::convertToListResponse);

    log.info(
        "{} 사전 목록 조회 완료 - 환경: {}, 전체: {}개",
        getDictionaryType(),
        environment,
        resultPage.getTotalElements());

    return PageResponse.from(resultPage);
  }

  public Response get(Long id, DictionaryEnvironmentType environment) {
    log.info("{} 사전 조회 - ID: {}, 환경: {}", getDictionaryType(), id, environment);

    T entity =
        getRepository()
            .findById(id)
            .orElseThrow(
                () -> new EntityNotFoundException(getDictionaryType() + " 사전을 찾을 수 없습니다: " + id));
    return convertToResponse(entity);
  }

  public Response update(Long id, UpdateReq request, DictionaryEnvironmentType environment) {
    log.info("{} 사전 수정 - ID: {}, 환경: {}", getDictionaryType(), id, environment);

    T entity =
        getRepository()
            .findById(id)
            .orElseThrow(
                () -> new EntityNotFoundException(getDictionaryType() + " 사전을 찾을 수 없습니다: " + id));

    updateEntity(entity, request);
    T updated = getRepository().save(entity);

    log.info("{} 사전 수정 완료 - ID: {}, 환경: {}", getDictionaryType(), id, environment);

    return convertToResponse(updated);
  }

  public void delete(Long id, DictionaryEnvironmentType environment) {
    log.info("{} 사전 삭제 - ID: {}, 환경: {}", getDictionaryType(), id, environment);

    if (!getRepository().existsById(id)) {
      throw new EntityNotFoundException(getDictionaryType() + " 사전을 찾을 수 없습니다: " + id);
    }

    getRepository().deleteById(id);
    log.info("{} 사전 삭제 완료 - ID: {}, 환경: {}", getDictionaryType(), id, environment);
  }

  @Override
  public void deployToDev() {
    log.info("개발 환경 {} 사전 배포 시작", getDictionaryType());

    List<T> currentDictionaries = findByEnvironmentType(DictionaryEnvironmentType.CURRENT);
    deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    List<T> devDictionaries =
        currentDictionaries.stream()
            .map(dict -> copyEntityWithEnvironment(dict, DictionaryEnvironmentType.DEV))
            .toList();

    getRepository().saveAll(devDictionaries);
    log.info("개발 환경 {} 사전 배포 완료: {}개", getDictionaryType(), devDictionaries.size());
  }

  @Override
  public void deployToProd() {
    log.info("운영 환경 {} 사전 배포 시작", getDictionaryType());

    List<T> devDictionaries = findByEnvironmentType(DictionaryEnvironmentType.DEV);
    if (devDictionaries.isEmpty()) {
      throw new IllegalStateException("개발 환경에 배포된 " + getDictionaryType() + " 사전이 없습니다.");
    }

    deleteByEnvironmentType(DictionaryEnvironmentType.PROD);

    List<T> prodDictionaries =
        devDictionaries.stream()
            .map(dict -> copyEntityWithEnvironment(dict, DictionaryEnvironmentType.PROD))
            .toList();

    getRepository().saveAll(prodDictionaries);
    log.info("운영 환경 {} 사전 배포 완료: {}개", getDictionaryType(), prodDictionaries.size());
  }

  protected Sort createSort(String sortBy, String sortDir, boolean unused) {
    if (sortBy == null || sortBy.trim().isEmpty()) {
      sortBy = "updatedAt";
    }
    if (sortDir == null || sortDir.trim().isEmpty()) {
      sortDir = "desc";
    }

    String[] allowedFields = {"keyword", "createdAt", "updatedAt"};

    if (!Arrays.asList(allowedFields).contains(sortBy)) {
      log.warn("허용되지 않은 정렬 필드: {}. 기본값 updatedAt 사용", sortBy);
      sortBy = "updatedAt";
    }

    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return Sort.by(direction, sortBy);
  }

  protected abstract T copyEntityWithEnvironment(T entity, DictionaryEnvironmentType environment);
}
