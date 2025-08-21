package com.yjlee.search.dictionary.category.service;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.dto.*;
import com.yjlee.search.dictionary.category.model.CategoryMapping;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionarySnapshot;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionaryRepository;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionarySnapshotRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;
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
public class CategoryRankingDictionaryService {

  private final CategoryRankingDictionaryRepository repository;
  private final CategoryRankingDictionarySnapshotRepository snapshotRepository;

  @Transactional
  public CategoryRankingDictionaryResponse create(
      CategoryRankingDictionaryCreateRequest request, DictionaryEnvironmentType environment) {
    log.info("카테고리 랭킹 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);

    List<CategoryMapping> mappings = convertToMappings(request.getCategoryMappings());

    if (environment == null || environment == DictionaryEnvironmentType.CURRENT) {
      // 현재 사전에 생성
      if (repository.existsByKeyword(request.getKeyword())) {
        throw new IllegalArgumentException("이미 존재하는 키워드입니다: " + request.getKeyword());
      }

      CategoryRankingDictionary dictionary =
          CategoryRankingDictionary.builder()
              .keyword(request.getKeyword())
              .categoryMappings(mappings)
              .description(request.getDescription())
              .build();

      CategoryRankingDictionary saved = repository.save(dictionary);
      log.info("카테고리 랭킹 사전 생성 완료: {} (ID: {}) - 환경: CURRENT", saved.getKeyword(), saved.getId());

      return convertToResponse(saved);
    } else {
      // DEV/PROD 스냅샷에 직접 생성
      if (snapshotRepository.existsByKeywordAndEnvironmentType(request.getKeyword(), environment)) {
        throw new IllegalArgumentException("이미 존재하는 키워드입니다: " + request.getKeyword());
      }

      CategoryRankingDictionarySnapshot snapshot =
          CategoryRankingDictionarySnapshot.builder()
              .environmentType(environment)
              .originalDictionaryId(0L)
              .keyword(request.getKeyword())
              .categoryMappings(mappings)
              .description(request.getDescription())
              .build();

      CategoryRankingDictionarySnapshot saved = snapshotRepository.save(snapshot);
      log.info(
          "카테고리 랭킹 사전 스냅샷 생성 완료: {} (ID: {}) - 환경: {}",
          saved.getKeyword(),
          saved.getId(),
          environment);

      return convertToResponseFromSnapshot(saved);
    }
  }

  @Transactional(readOnly = true)
  public PageResponse<CategoryRankingDictionaryListResponse> getList(
      int page,
      int size,
      String search,
      String sortBy,
      String sortDir,
      DictionaryEnvironmentType environmentType) {

    log.debug(
        "카테고리 랭킹 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environmentType);

    if (environmentType == null || environmentType == DictionaryEnvironmentType.CURRENT) {
      return getListFromCurrent(page, size, search, sortBy, sortDir);
    } else {
      return getListFromSnapshot(page, size, search, sortBy, sortDir, environmentType);
    }
  }

  private PageResponse<CategoryRankingDictionaryListResponse> getListFromCurrent(
      int page, int size, String search, String sortBy, String sortDir) {

    Sort sort = createSort(sortBy, sortDir, false);
    Pageable pageable = PageRequest.of(Math.max(0, page), size, sort);

    Page<CategoryRankingDictionary> dictionaryPage;
    if (search != null && !search.trim().isEmpty()) {
      dictionaryPage = repository.findByKeywordContainingIgnoreCase(search.trim(), pageable);
    } else {
      dictionaryPage = repository.findAll(pageable);
    }

    return PageResponse.from(dictionaryPage.map(this::convertToListResponse));
  }

  private PageResponse<CategoryRankingDictionaryListResponse> getListFromSnapshot(
      int page,
      int size,
      String search,
      String sortBy,
      String sortDir,
      DictionaryEnvironmentType environmentType) {

    Sort sort = createSort(sortBy, sortDir, true);
    Pageable pageable = PageRequest.of(Math.max(0, page), size, sort);

    Page<CategoryRankingDictionarySnapshot> snapshotPage;
    if (search != null && !search.trim().isEmpty()) {
      snapshotPage =
          snapshotRepository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
              environmentType, search.trim(), pageable);
    } else {
      snapshotPage = snapshotRepository.findByEnvironmentType(environmentType, pageable);
    }

    return PageResponse.from(snapshotPage.map(this::convertToListResponseFromSnapshot));
  }

  @Transactional(readOnly = true)
  public CategoryRankingDictionaryResponse getDetail(
      Long id, DictionaryEnvironmentType environment) {
    log.debug("카테고리 랭킹 사전 상세 조회: {} - 환경: {}", id, environment);

    if (environment == null || environment == DictionaryEnvironmentType.CURRENT) {
      CategoryRankingDictionary dictionary =
          repository
              .findById(id)
              .orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id));
      return convertToResponse(dictionary);
    } else {
      CategoryRankingDictionarySnapshot snapshot =
          snapshotRepository
              .findByOriginalDictionaryIdAndEnvironmentType(id, environment)
              .orElseThrow(() -> new EntityNotFoundException("스냅샷을 찾을 수 없습니다: " + id));
      return convertToResponseFromSnapshot(snapshot);
    }
  }

  @Transactional
  public CategoryRankingDictionaryResponse update(
      Long id,
      CategoryRankingDictionaryUpdateRequest request,
      DictionaryEnvironmentType environment) {
    log.info("카테고리 랭킹 사전 수정 요청: {} - 환경: {}", id, environment);

    if (environment == null || environment == DictionaryEnvironmentType.CURRENT) {
      // 현재 사전 수정
      CategoryRankingDictionary existing =
          repository
              .findById(id)
              .orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id));

      if (request.getKeyword() != null) {
        if (!existing.getKeyword().equals(request.getKeyword())
            && repository.existsByKeyword(request.getKeyword())) {
          throw new IllegalArgumentException("이미 존재하는 키워드입니다: " + request.getKeyword());
        }
        existing.updateKeyword(request.getKeyword());
      }

      if (request.getCategoryMappings() != null) {
        List<CategoryMapping> mappings = convertToMappings(request.getCategoryMappings());
        existing.updateCategoryMappings(mappings);
      }

      if (request.getDescription() != null) {
        existing.updateDescription(request.getDescription());
      }

      CategoryRankingDictionary updated = repository.save(existing);
      log.info("카테고리 랭킹 사전 수정 완료: {}", id);

      return convertToResponse(updated);
    } else {
      // DEV/PROD 스냅샷 수정
      CategoryRankingDictionarySnapshot existing =
          snapshotRepository
              .findById(id)
              .orElseThrow(() -> new EntityNotFoundException("스냅샷을 찾을 수 없습니다: " + id));

      if (request.getKeyword() != null) {
        if (!existing.getKeyword().equals(request.getKeyword())
            && snapshotRepository.existsByKeywordAndEnvironmentType(
                request.getKeyword(), environment)) {
          throw new IllegalArgumentException("이미 존재하는 키워드입니다: " + request.getKeyword());
        }
        existing.setKeyword(request.getKeyword());
      }

      if (request.getCategoryMappings() != null) {
        List<CategoryMapping> mappings = convertToMappings(request.getCategoryMappings());
        existing.setCategoryMappings(mappings);
      }

      if (request.getDescription() != null) {
        existing.setDescription(request.getDescription());
      }

      CategoryRankingDictionarySnapshot updated = snapshotRepository.save(existing);
      log.info("카테고리 랭킹 사전 스냅샷 수정 완료: {} - 환경: {}", id, environment);

      return convertToResponseFromSnapshot(updated);
    }
  }

  @Transactional
  public void delete(Long id, DictionaryEnvironmentType environment) {
    log.info("카테고리 랭킹 사전 삭제 요청: {} - 환경: {}", id, environment);

    if (environment == null || environment == DictionaryEnvironmentType.CURRENT) {
      // 현재 사전에서 삭제
      if (!repository.existsById(id)) {
        throw new EntityNotFoundException("사전을 찾을 수 없습니다: " + id);
      }

      repository.deleteById(id);
      log.info("카테고리 랭킹 사전 삭제 완료: {} - 환경: CURRENT", id);
    } else {
      // DEV/PROD 스냅샷에서 삭제
      if (!snapshotRepository.existsById(id)) {
        throw new EntityNotFoundException("스냅샷을 찾을 수 없습니다: " + id);
      }

      snapshotRepository.deleteById(id);
      log.info("카테고리 랭킹 사전 스냅샷 삭제 완료: {} - 환경: {}", id, environment);
    }
  }

  @Transactional(readOnly = true)
  public CategoryRankingDictionaryResponse getByKeyword(
      String keyword, DictionaryEnvironmentType environment) {
    log.debug("키워드로 카테고리 랭킹 사전 조회: {} - 환경: {}", keyword, environment);

    if (environment == null || environment == DictionaryEnvironmentType.CURRENT) {
      CategoryRankingDictionary dictionary =
          repository
              .findByKeyword(keyword)
              .orElseThrow(() -> new EntityNotFoundException("키워드를 찾을 수 없습니다: " + keyword));
      return convertToResponse(dictionary);
    } else {
      CategoryRankingDictionarySnapshot snapshot =
          snapshotRepository
              .findByKeywordAndEnvironmentType(keyword, environment)
              .orElseThrow(() -> new EntityNotFoundException("스냅샷을 찾을 수 없습니다: " + keyword));
      return convertToResponseFromSnapshot(snapshot);
    }
  }

  @Transactional(readOnly = true)
  public CategoryListResponse getCategories(DictionaryEnvironmentType environment) {
    log.debug("전체 카테고리 목록 조회 - 환경: {}", environment);

    List<String> categories = repository.findDistinctCategories();

    return CategoryListResponse.builder()
        .totalCount(categories.size())
        .categories(categories)
        .build();
  }

  @Transactional(readOnly = true)
  public PageResponse<CategoryRankingDictionaryResponse> getAllWithMappings(
      DictionaryEnvironmentType environment) {
    log.debug("전체 카테고리 랭킹 사전 조회 (매핑 포함) - 환경: {}", environment);

    if (environment == null || environment == DictionaryEnvironmentType.CURRENT) {
      List<CategoryRankingDictionary> dictionaries = repository.findAll();
      List<CategoryRankingDictionaryResponse> responses =
          dictionaries.stream().map(this::convertToResponse).collect(Collectors.toList());

      return new PageResponse<>(responses, 0, responses.size(), (long) responses.size(), 1);
    } else {
      List<CategoryRankingDictionarySnapshot> snapshots =
          snapshotRepository.findByEnvironmentType(environment);
      List<CategoryRankingDictionaryResponse> responses =
          snapshots.stream().map(this::convertToResponseFromSnapshot).collect(Collectors.toList());

      return new PageResponse<>(responses, 0, responses.size(), (long) responses.size(), 1);
    }
  }

  @Transactional
  public void createDevSnapshot() {
    log.info("개발 환경 카테고리 랭킹 사전 스냅샷 생성 시작");

    List<CategoryRankingDictionary> currentDictionaries = repository.findAll();
    if (currentDictionaries.isEmpty()) {
      log.warn("스냅샷으로 저장할 카테고리 랭킹 사전이 없습니다.");
      return;
    }

    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    List<CategoryRankingDictionarySnapshot> snapshots =
        currentDictionaries.stream()
            .map(
                dict ->
                    CategoryRankingDictionarySnapshot.createSnapshot(
                        DictionaryEnvironmentType.DEV, dict))
            .toList();

    snapshotRepository.saveAll(snapshots);
    log.info("개발 환경 카테고리 랭킹 사전 스냅샷 생성 완료: {}개", snapshots.size());
  }

  @Transactional
  public void deployToDev() {
    log.info("개발 환경 카테고리 랭킹 사전 배포 시작");

    List<CategoryRankingDictionary> currentDictionaries = repository.findAllByOrderByKeywordAsc();

    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    List<CategoryRankingDictionarySnapshot> devSnapshots =
        currentDictionaries.stream()
            .map(
                dict ->
                    CategoryRankingDictionarySnapshot.createSnapshot(
                        DictionaryEnvironmentType.DEV, dict))
            .toList();

    snapshotRepository.saveAll(devSnapshots);
    log.info("개발 환경 카테고리 랭킹 사전 배포 완료: {}개", devSnapshots.size());
  }

  @Transactional
  public void deployToProd() {
    log.info("운영 환경 카테고리 랭킹 사전 스냅샷 배포 시작");

    List<CategoryRankingDictionarySnapshot> devSnapshots =
        snapshotRepository.findByEnvironmentTypeOrderByKeywordAsc(DictionaryEnvironmentType.DEV);

    snapshotRepository.deleteByEnvironmentType(DictionaryEnvironmentType.PROD);

    List<CategoryRankingDictionarySnapshot> prodSnapshots =
        devSnapshots.stream()
            .map(
                devSnapshot ->
                    CategoryRankingDictionarySnapshot.builder()
                        .environmentType(DictionaryEnvironmentType.PROD)
                        .originalDictionaryId(devSnapshot.getOriginalDictionaryId())
                        .keyword(devSnapshot.getKeyword())
                        .categoryMappings(devSnapshot.getCategoryMappings())
                        .description(devSnapshot.getDescription())
                        .build())
            .toList();

    snapshotRepository.saveAll(prodSnapshots);
    log.info("운영 환경 카테고리 랭킹 사전 스냅샷 배포 완료: {}개", prodSnapshots.size());
  }

  private Sort createSort(String sortBy, String sortDir, boolean isSnapshot) {
    if (sortBy == null || sortBy.trim().isEmpty()) {
      sortBy = isSnapshot ? "createdAt" : "updatedAt";
    }
    if (sortDir == null || sortDir.trim().isEmpty()) {
      sortDir = "desc";
    }

    String[] allowedFields = {"keyword", "createdAt", "updatedAt"};

    boolean isValidField = false;
    for (String field : allowedFields) {
      if (field.equals(sortBy)) {
        isValidField = true;
        break;
      }
    }

    if (!isValidField) {
      log.warn("허용되지 않은 정렬 필드: {}. 기본값 {} 사용", sortBy, isSnapshot ? "createdAt" : "updatedAt");
      sortBy = isSnapshot ? "createdAt" : "updatedAt";
    }

    Sort.Direction direction =
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

    return Sort.by(direction, sortBy);
  }

  private List<CategoryMapping> convertToMappings(List<CategoryMappingDto> dtos) {
    if (dtos == null) {
      return List.of();
    }
    return dtos.stream()
        .map(dto -> new CategoryMapping(dto.getCategory(), dto.getWeight()))
        .collect(Collectors.toList());
  }

  private CategoryRankingDictionaryResponse convertToResponse(CategoryRankingDictionary entity) {
    return CategoryRankingDictionaryResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .categoryMappings(
            CategoryRankingDictionaryResponse.convertMappings(entity.getCategoryMappings()))
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private CategoryRankingDictionaryResponse convertToResponseFromSnapshot(
      CategoryRankingDictionarySnapshot snapshot) {
    return CategoryRankingDictionaryResponse.builder()
        .id(snapshot.getOriginalDictionaryId())
        .keyword(snapshot.getKeyword())
        .categoryMappings(
            CategoryRankingDictionaryResponse.convertMappings(snapshot.getCategoryMappings()))
        .description(snapshot.getDescription())
        .createdAt(snapshot.getCreatedAt())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }

  private CategoryRankingDictionaryListResponse convertToListResponse(
      CategoryRankingDictionary entity) {
    return CategoryRankingDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .categoryCount(
            entity.getCategoryMappings() != null ? entity.getCategoryMappings().size() : 0)
        .description(entity.getDescription())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private CategoryRankingDictionaryListResponse convertToListResponseFromSnapshot(
      CategoryRankingDictionarySnapshot snapshot) {
    return CategoryRankingDictionaryListResponse.builder()
        .id(snapshot.getOriginalDictionaryId())
        .keyword(snapshot.getKeyword())
        .categoryCount(
            snapshot.getCategoryMappings() != null ? snapshot.getCategoryMappings().size() : 0)
        .description(snapshot.getDescription())
        .updatedAt(snapshot.getUpdatedAt())
        .build();
  }
}
