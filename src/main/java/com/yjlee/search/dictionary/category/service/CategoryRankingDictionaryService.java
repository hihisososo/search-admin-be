package com.yjlee.search.dictionary.category.service;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.category.dto.*;
import com.yjlee.search.dictionary.category.mapper.CategoryRankingDictionaryMapper;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionaryRepository;
import com.yjlee.search.dictionary.common.enums.DictionarySortField;
import com.yjlee.search.dictionary.common.service.DictionaryService;
import com.yjlee.search.index.repository.ProductRepository;
import com.yjlee.search.search.service.category.CategoryRankingService;
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
public class CategoryRankingDictionaryService implements DictionaryService {

  private final CategoryRankingDictionaryRepository repository;
  private final ProductRepository productRepository;
  private final CategoryRankingService categoryRankingService;
  private final CategoryRankingDictionaryMapper mapper;

  @Transactional
  public CategoryRankingDictionaryResponse create(
      CategoryRankingDictionaryCreateRequest request, EnvironmentType environment) {
    if (repository.existsByKeywordAndEnvironmentType(request.getKeyword(), environment)) {
      throw new IllegalArgumentException("이미 존재하는 키워드입니다: " + request.getKeyword());
    }
    return mapper.toResponse(
        repository.save(
            CategoryRankingDictionary.of(
                request.getKeyword(),
                mapper.toMappings(request.getCategoryMappings()),
                request.getDescription(),
                environment)));
  }

  @Transactional(readOnly = true)
  public PageResponse<CategoryRankingDictionaryListResponse> getList(
      int page,
      int size,
      String search,
      String sortBy,
      String sortDir,
      EnvironmentType environmentType) {
    Pageable pageable = PageRequest.of(Math.max(0, page), size, createSort(sortBy, sortDir));
    Page<CategoryRankingDictionary> dictionaryPage =
        (search != null && !search.trim().isEmpty())
            ? repository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
                environmentType, search.trim(), pageable)
            : repository.findByEnvironmentType(environmentType, pageable);
    return PageResponse.from(dictionaryPage.map(mapper::toListResponse));
  }

  @Transactional(readOnly = true)
  public CategoryRankingDictionaryResponse getDetail(Long id) {
    return mapper.toResponse(
        repository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id)));
  }

  @Transactional
  public CategoryRankingDictionaryResponse update(
      Long id, CategoryRankingDictionaryUpdateRequest request, EnvironmentType environment) {
    CategoryRankingDictionary existing =
        repository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id));

    if (request.getKeyword() != null) {
      if (!existing.getKeyword().equals(request.getKeyword())
          && repository.existsByKeywordAndEnvironmentType(request.getKeyword(), environment)) {
        throw new IllegalArgumentException("이미 존재하는 키워드입니다: " + request.getKeyword());
      }
      existing.updateKeyword(request.getKeyword());
    }
    if (request.getCategoryMappings() != null) {
      existing.updateCategoryMappings(mapper.toMappings(request.getCategoryMappings()));
    }
    if (request.getDescription() != null) {
      existing.updateDescription(request.getDescription());
    }
    return mapper.toResponse(repository.save(existing));
  }

  @Transactional
  public void delete(Long id) {
    repository.findById(id).orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id));
    repository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public CategoryListResponse getCategories(EnvironmentType environment) {
    List<String> categories = productRepository.findDistinctCategoryNames();
    categories.sort(String::compareTo);
    return CategoryListResponse.builder()
        .totalCount(categories.size())
        .categories(categories)
        .build();
  }

  @Override
  @Transactional
  public void preIndexing() {
    deployToEnvironment(EnvironmentType.CURRENT, EnvironmentType.DEV);
  }

  @Override
  @Transactional
  public void preDeploy() {
    deployToEnvironment(EnvironmentType.DEV, EnvironmentType.PROD);
  }

  @Override
  public void deployToTemp() {
    log.debug("카테고리 랭킹 사전 임시 환경 배포 건너뛰기 - 캐시 기반 동작");
  }

  private void deployToEnvironment(EnvironmentType from, EnvironmentType to) {
    List<CategoryRankingDictionary> sourceDictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(from);
    if (sourceDictionaries.isEmpty()) return;

    repository.deleteByEnvironmentType(to);

    List<CategoryRankingDictionary> targetDictionaries =
        sourceDictionaries.stream().map(dict -> mapper.copyWithEnvironment(dict, to)).toList();
    repository.saveAll(targetDictionaries);
    log.info("{} 환경 카테고리 랭킹 사전 배포 완료: {}개", to, targetDictionaries.size());
  }

  private Sort createSort(String sortBy, String sortDir) {
    return Sort.by(
        "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC,
        DictionarySortField.getValidFieldOrDefault(sortBy));
  }

  @Override
  public void realtimeSync(EnvironmentType environment) {
    categoryRankingService.updateCacheRealtime(environment);
  }

  @Transactional
  public void deleteByEnvironmentType(EnvironmentType environment) {
    repository.deleteByEnvironmentType(environment);
  }
}
