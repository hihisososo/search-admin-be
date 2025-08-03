package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.dto.BaseDictionaryCreateRequest;
import com.yjlee.search.dictionary.common.dto.BaseDictionaryResponse;
import com.yjlee.search.dictionary.common.model.DictionaryEntity;
import com.yjlee.search.dictionary.common.model.DictionarySnapshotEntity;
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
    S extends DictionarySnapshotEntity,
    CreateReq extends BaseDictionaryCreateRequest,
    UpdateReq,
    Response extends BaseDictionaryResponse,
    ListResponse> {

  protected abstract JpaRepository<T, Long> getRepository();

  protected abstract JpaRepository<S, Long> getSnapshotRepository();

  protected abstract String getDictionaryType();

  protected abstract T buildEntity(CreateReq request);

  protected abstract S createSnapshot(DictionaryEnvironmentType env, T entity);

  protected abstract Response convertToResponse(T entity);

  protected abstract Response convertToResponse(S snapshot);

  protected abstract ListResponse convertToListResponse(T entity);

  protected abstract ListResponse convertToListResponse(S snapshot);

  protected abstract void updateEntity(T entity, UpdateReq request);

  protected abstract Page<T> searchInRepository(String keyword, Pageable pageable);

  protected abstract Page<S> searchInSnapshotRepository(
      String keyword, DictionaryEnvironmentType environment, Pageable pageable);

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
      int page, int size, String sortBy, String sortDir, String keyword, DictionaryEnvironmentType environment) {
    log.info(
        "{} 사전 목록 조회 - 환경: {}, 페이지: {}, 크기: {}, 정렬: {} {}, 검색어: {}",
        getDictionaryType(),
        environment,
        page,
        size,
        sortBy,
        sortDir,
        keyword);

    Sort sort = createSort(sortBy, sortDir, environment != DictionaryEnvironmentType.CURRENT);
    Pageable pageable = PageRequest.of(page, size, sort);

    Page<ListResponse> resultPage;

    if (environment == DictionaryEnvironmentType.CURRENT) {
      Page<T> entities =
          (keyword != null && !keyword.trim().isEmpty())
              ? searchInRepository(keyword.trim(), pageable)
              : getRepository().findAll(pageable);
      resultPage = entities.map(this::convertToListResponse);
    } else {
      Page<S> snapshots =
          (keyword != null && !keyword.trim().isEmpty())
              ? searchInSnapshotRepository(keyword.trim(), environment, pageable)
              : findSnapshotsByEnvironment(environment, pageable);
      resultPage = snapshots.map(this::convertToListResponse);
    }

    log.info("{} 사전 목록 조회 완료 - 환경: {}, 전체: {}개", getDictionaryType(), environment, resultPage.getTotalElements());

    return PageResponse.from(resultPage);
  }

  public Response get(Long id, DictionaryEnvironmentType environment) {
    log.info("{} 사전 조회 - ID: {}, 환경: {}", getDictionaryType(), id, environment);

    if (environment == DictionaryEnvironmentType.CURRENT) {
      T entity =
          getRepository()
              .findById(id)
              .orElseThrow(() -> new EntityNotFoundException(getDictionaryType() + " 사전을 찾을 수 없습니다: " + id));
      return convertToResponse(entity);
    } else {
      S snapshot =
          findSnapshotByOriginalIdAndEnvironment(id, environment)
              .orElseThrow(() -> new EntityNotFoundException(getDictionaryType() + " 사전 스냅샷을 찾을 수 없습니다: " + id));
      return convertToResponse(snapshot);
    }
  }

  public Response update(Long id, UpdateReq request, DictionaryEnvironmentType environment) {
    log.info("{} 사전 수정 - ID: {}, 환경: {}", getDictionaryType(), id, environment);

    T entity =
        getRepository()
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException(getDictionaryType() + " 사전을 찾을 수 없습니다: " + id));

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

  public void createDevSnapshot() {
    log.info("개발 환경 {} 사전 스냅샷 생성 시작", getDictionaryType());

    List<T> currentDictionaries = getRepository().findAll(Sort.by(Sort.Direction.ASC, "keyword"));
    deleteSnapshotsByEnvironment(DictionaryEnvironmentType.DEV);

    List<S> devSnapshots =
        currentDictionaries.stream()
            .map(dict -> createSnapshot(DictionaryEnvironmentType.DEV, dict))
            .toList();

    getSnapshotRepository().saveAll(devSnapshots);
    log.info("개발 환경 {} 사전 스냅샷 생성 완료: {}개", getDictionaryType(), devSnapshots.size());
  }

  public void deployToDev() {
    log.info("개발 환경 {} 사전 배포 시작", getDictionaryType());

    List<T> currentDictionaries = getRepository().findAll(Sort.by(Sort.Direction.ASC, "keyword"));
    deleteSnapshotsByEnvironment(DictionaryEnvironmentType.DEV);

    List<S> devSnapshots =
        currentDictionaries.stream()
            .map(dict -> createSnapshot(DictionaryEnvironmentType.DEV, dict))
            .toList();

    getSnapshotRepository().saveAll(devSnapshots);
    log.info("개발 환경 {} 사전 배포 완료: {}개", getDictionaryType(), devSnapshots.size());
  }

  public void deployToProd() {
    log.info("운영 환경 {} 사전 배포 시작", getDictionaryType());

    List<S> devSnapshots = findAllSnapshotsByEnvironment(DictionaryEnvironmentType.DEV);
    if (devSnapshots.isEmpty()) {
      throw new IllegalStateException("개발 환경에 배포된 " + getDictionaryType() + " 사전이 없습니다.");
    }

    deleteSnapshotsByEnvironment(DictionaryEnvironmentType.PROD);

    List<S> prodSnapshots =
        devSnapshots.stream()
            .map(devSnapshot -> createSnapshotFromSnapshot(DictionaryEnvironmentType.PROD, devSnapshot))
            .toList();

    getSnapshotRepository().saveAll(prodSnapshots);
    log.info("운영 환경 {} 사전 배포 완료: {}개", getDictionaryType(), prodSnapshots.size());
  }

  protected Sort createSort(String sortBy, String sortDir, boolean isSnapshot) {
    if (sortBy == null || sortBy.trim().isEmpty()) {
      sortBy = isSnapshot ? "createdAt" : "updatedAt";
    }
    if (sortDir == null || sortDir.trim().isEmpty()) {
      sortDir = "desc";
    }

    String[] allowedFields = {"keyword", "createdAt", "updatedAt"};

    if (!Arrays.asList(allowedFields).contains(sortBy)) {
      log.warn(
          "허용되지 않은 정렬 필드: {}. 기본값 {} 사용",
          sortBy,
          isSnapshot ? "createdAt" : "updatedAt");
      sortBy = isSnapshot ? "createdAt" : "updatedAt";
    }

    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return Sort.by(direction, sortBy);
  }

  protected abstract S createSnapshotFromSnapshot(DictionaryEnvironmentType env, S snapshot);

  protected abstract Page<S> findSnapshotsByEnvironment(
      DictionaryEnvironmentType environment, Pageable pageable);

  protected abstract java.util.Optional<S> findSnapshotByOriginalIdAndEnvironment(
      Long originalId, DictionaryEnvironmentType environment);

  protected abstract List<S> findAllSnapshotsByEnvironment(DictionaryEnvironmentType environment);

  protected abstract void deleteSnapshotsByEnvironment(DictionaryEnvironmentType environment);
}