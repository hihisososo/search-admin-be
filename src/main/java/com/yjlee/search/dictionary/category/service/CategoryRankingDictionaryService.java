package com.yjlee.search.dictionary.category.service;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.category.dto.*;
import com.yjlee.search.dictionary.category.model.CategoryMapping;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionaryRepository;
import com.yjlee.search.index.repository.ProductRepository;
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
public class CategoryRankingDictionaryService {

  private final CategoryRankingDictionaryRepository repository;
  private final ProductRepository productRepository;

  @Transactional
  public CategoryRankingDictionaryResponse create(
      CategoryRankingDictionaryCreateRequest request, EnvironmentType environment) {
    if (repository.existsByKeywordAndEnvironmentType(request.getKeyword(), environment)) {
      throw new IllegalArgumentException("이미 존재하는 키워드입니다: " + request.getKeyword());
    }
    CategoryRankingDictionary saved =
        repository.save(
            CategoryRankingDictionary.of(
                request.getKeyword(),
                CategoryMapping.fromDtos(request.getCategoryMappings()),
                environment));
    return CategoryRankingDictionaryResponse.from(saved);
  }

  @Transactional(readOnly = true)
  public PageResponse<CategoryRankingDictionaryListResponse> getList(
      Pageable pageable, String search, EnvironmentType environmentType) {
    Page<CategoryRankingDictionary> dictionaryPage =
        (search != null && !search.trim().isEmpty())
            ? repository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
                environmentType, search.trim(), pageable)
            : repository.findByEnvironmentType(environmentType, pageable);
    return PageResponse.from(dictionaryPage.map(CategoryRankingDictionaryListResponse::from));
  }

  @Transactional(readOnly = true)
  public CategoryRankingDictionaryResponse getDetail(Long id) {
    CategoryRankingDictionary dictionary =
        repository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id));
    return CategoryRankingDictionaryResponse.from(dictionary);
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
      existing.updateCategoryMappings(CategoryMapping.fromDtos(request.getCategoryMappings()));
    }
    CategoryRankingDictionary updated = repository.save(existing);
    return CategoryRankingDictionaryResponse.from(updated);
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

  @Transactional
  public void deleteByEnvironmentType(EnvironmentType environment) {
    repository.deleteByEnvironmentType(environment);
  }

  @Transactional
  public void saveToEnvironment(
      List<CategoryRankingDictionary> sourceData, EnvironmentType targetEnv) {
    // 기존 환경 데이터 삭제
    deleteByEnvironmentType(targetEnv);
    repository.flush(); // delete를 DB에 즉시 반영

    if (sourceData == null || sourceData.isEmpty()) {
      return;
    }

    List<CategoryRankingDictionary> targetDictionaries =
        sourceData.stream()
            .map(
                dict ->
                    CategoryRankingDictionary.of(
                        dict.getKeyword(), dict.getCategoryMappings(), targetEnv))
            .toList();

    repository.saveAll(targetDictionaries);
    log.info("{} 환경 카테고리 랭킹 사전 저장 완료: {}개", targetEnv, targetDictionaries.size());
  }
}
