package com.yjlee.search.dictionary.category.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.EnvironmentTypeConverter;
import com.yjlee.search.dictionary.category.dto.*;
import com.yjlee.search.dictionary.category.model.CategoryMapping;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionaryRepository;
import com.yjlee.search.dictionary.common.service.DictionaryService;
import com.yjlee.search.index.repository.ProductRepository;
import com.yjlee.search.search.service.IndexResolver;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
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
public class CategoryRankingDictionaryService implements DictionaryService {

  private final CategoryRankingDictionaryRepository repository;
  private final ProductRepository productRepository;
  private final ElasticsearchClient elasticsearchClient;
  private final IndexResolver indexResolver;

  @Override
  public String getDictionaryTypeEnum() {
    return "CATEGORY";
  }

  @Transactional
  public CategoryRankingDictionaryResponse create(
      CategoryRankingDictionaryCreateRequest request, DictionaryEnvironmentType environment) {
    log.info("카테고리 랭킹 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);

    List<CategoryMapping> mappings = convertToMappings(request.getCategoryMappings());

    if (repository.existsByKeywordAndEnvironmentType(request.getKeyword(), environment)) {
      throw new IllegalArgumentException("이미 존재하는 키워드입니다: " + request.getKeyword());
    }

    CategoryRankingDictionary dictionary =
        CategoryRankingDictionary.builder()
            .environmentType(environment)
            .keyword(request.getKeyword())
            .categoryMappings(mappings)
            .description(request.getDescription())
            .build();

    CategoryRankingDictionary saved = repository.save(dictionary);
    log.info(
        "카테고리 랭킹 사전 생성 완료: {} (ID: {}) - 환경: {}", saved.getKeyword(), saved.getId(), environment);

    return convertToResponse(saved);
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

    Sort sort = createSort(sortBy, sortDir);
    Pageable pageable = PageRequest.of(Math.max(0, page), size, sort);

    Page<CategoryRankingDictionary> dictionaryPage;
    if (search != null && !search.trim().isEmpty()) {
      dictionaryPage =
          repository.findByEnvironmentTypeAndKeywordContainingIgnoreCase(
              environmentType, search.trim(), pageable);
    } else {
      dictionaryPage = repository.findByEnvironmentType(environmentType, pageable);
    }

    return PageResponse.from(dictionaryPage.map(this::convertToListResponse));
  }

  @Transactional(readOnly = true)
  public CategoryRankingDictionaryResponse getDetail(
      Long id, DictionaryEnvironmentType environment) {
    log.debug("카테고리 랭킹 사전 상세 조회: {} - 환경: {}", id, environment);

    CategoryRankingDictionary dictionary =
        repository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("사전을 찾을 수 없습니다: " + id));
    return convertToResponse(dictionary);
  }

  @Transactional
  public CategoryRankingDictionaryResponse update(
      Long id,
      CategoryRankingDictionaryUpdateRequest request,
      DictionaryEnvironmentType environment) {
    log.info("카테고리 랭킹 사전 수정 요청: {} - 환경: {}", id, environment);

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
      List<CategoryMapping> mappings = convertToMappings(request.getCategoryMappings());
      existing.updateCategoryMappings(mappings);
    }

    if (request.getDescription() != null) {
      existing.updateDescription(request.getDescription());
    }

    CategoryRankingDictionary updated = repository.save(existing);
    log.info("카테고리 랭킹 사전 수정 완료: {}", id);

    return convertToResponse(updated);
  }

  @Transactional
  public void delete(Long id, DictionaryEnvironmentType environment) {
    log.info("카테고리 랭킹 사전 삭제 요청: {} - 환경: {}", id, environment);

    if (!repository.existsById(id)) {
      throw new EntityNotFoundException("사전을 찾을 수 없습니다: " + id);
    }

    repository.deleteById(id);
    log.info("카테고리 랭킹 사전 삭제 완료: {} - 환경: {}", id, environment);
  }

  @Transactional(readOnly = true)
  public CategoryRankingDictionaryResponse getByKeyword(
      String keyword, DictionaryEnvironmentType environment) {
    log.debug("키워드로 카테고리 랭킹 사전 조회: {} - 환경: {}", keyword, environment);

    CategoryRankingDictionary dictionary =
        repository
            .findByKeywordAndEnvironmentType(keyword, environment)
            .orElseThrow(() -> new EntityNotFoundException("키워드를 찾을 수 없습니다: " + keyword));
    return convertToResponse(dictionary);
  }

  @Transactional(readOnly = true)
  public CategoryListResponse getCategories(DictionaryEnvironmentType environment) {
    log.debug("전체 카테고리 목록 조회 - 환경: {}", environment);

    List<String> categories;

    // CURRENT 환경이면 products 테이블에서 카테고리 조회
    if (environment == null || environment == DictionaryEnvironmentType.CURRENT) {
      log.debug("CURRENT 환경 - products 테이블에서 카테고리 조회");
      categories = productRepository.findDistinctCategoryNames();
    } else {
      // DEV, PROD 환경이면 Elasticsearch에서 aggregation으로 조회
      log.debug("{} 환경 - Elasticsearch에서 카테고리 조회", environment);
      categories = getCategoriesFromElasticsearch(environment);
    }

    return CategoryListResponse.builder()
        .totalCount(categories.size())
        .categories(categories)
        .build();
  }

  private List<String> getCategoriesFromElasticsearch(DictionaryEnvironmentType environment) {
    try {
      // 환경에 맞는 인덱스 이름 가져오기
      String indexName =
          indexResolver.resolveProductIndex(
              EnvironmentTypeConverter.toIndexEnvironmentType(environment));

      log.debug("Elasticsearch 카테고리 조회 - 인덱스: {}", indexName);

      // Aggregation 쿼리 생성
      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(indexName)
                      .size(0) // 문서는 가져오지 않고 aggregation만
                      .aggregations(
                          "categories",
                          Aggregation.of(
                              a ->
                                  a.terms(
                                      t ->
                                          t.field(ESFields.CATEGORY_NAME)
                                              .size(10000) // 최대 10000개 카테고리
                                      ))));

      // 쿼리 실행
      SearchResponse<JsonNode> response = elasticsearchClient.search(searchRequest, JsonNode.class);

      // 결과 파싱
      List<String> categories = new ArrayList<>();
      var categoriesAgg = response.aggregations().get("categories");
      if (categoriesAgg != null && categoriesAgg.sterms() != null) {
        var buckets = categoriesAgg.sterms().buckets();
        for (var bucket : buckets.array()) {
          String categoryName = bucket.key().stringValue();
          if (categoryName != null && !categoryName.isEmpty()) {
            categories.add(categoryName);
          }
        }
      }

      // 알파벳 순 정렬
      categories.sort(String::compareTo);

      log.info("Elasticsearch에서 {}개 카테고리 조회 완료", categories.size());
      return categories;

    } catch (IOException e) {
      log.error("Elasticsearch 카테고리 조회 실패", e);
      throw new RuntimeException("카테고리 조회 중 오류 발생", e);
    }
  }

  @Transactional(readOnly = true)
  public PageResponse<CategoryRankingDictionaryResponse> getAllWithMappings(
      DictionaryEnvironmentType environment) {
    log.debug("전체 카테고리 랭킹 사전 조회 (매핑 포함) - 환경: {}", environment);

    List<CategoryRankingDictionary> dictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(environment);
    List<CategoryRankingDictionaryResponse> responses =
        dictionaries.stream().map(this::convertToResponse).collect(Collectors.toList());

    return new PageResponse<>(responses, 0, responses.size(), (long) responses.size(), 1);
  }

  @Override
  @Transactional
  public void deployToDev() {
    log.info("개발 환경 카테고리 랭킹 사전 배포 시작");

    List<CategoryRankingDictionary> currentDictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(DictionaryEnvironmentType.CURRENT);
    if (currentDictionaries.isEmpty()) {
      log.warn("배포할 카테고리 랭킹 사전이 없습니다.");
      return;
    }

    // 기존 개발 환경 데이터 삭제
    repository.deleteByEnvironmentType(DictionaryEnvironmentType.DEV);

    // CURRENT 데이터를 DEV로 복사
    List<CategoryRankingDictionary> devDictionaries =
        currentDictionaries.stream()
            .map(
                dict ->
                    CategoryRankingDictionary.builder()
                        .environmentType(DictionaryEnvironmentType.DEV)
                        .keyword(dict.getKeyword())
                        .categoryMappings(dict.getCategoryMappings())
                        .description(dict.getDescription())
                        .build())
            .toList();

    repository.saveAll(devDictionaries);
    log.info("개발 환경 카테고리 랭킹 사전 배포 완료: {}개", devDictionaries.size());
  }

  @Override
  @Transactional
  public void deployToProd() {
    log.info("운영 환경 카테고리 랭킹 사전 배포 시작");

    List<CategoryRankingDictionary> currentDictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(DictionaryEnvironmentType.CURRENT);
    if (currentDictionaries.isEmpty()) {
      throw new IllegalStateException("배포할 카테고리 랭킹 사전이 없습니다.");
    }

    // 기존 운영 환경 데이터 삭제
    repository.deleteByEnvironmentType(DictionaryEnvironmentType.PROD);

    // CURRENT 데이터를 PROD로 복사
    List<CategoryRankingDictionary> prodDictionaries =
        currentDictionaries.stream()
            .map(
                dict ->
                    CategoryRankingDictionary.builder()
                        .environmentType(DictionaryEnvironmentType.PROD)
                        .keyword(dict.getKeyword())
                        .categoryMappings(dict.getCategoryMappings())
                        .description(dict.getDescription())
                        .build())
            .toList();

    repository.saveAll(prodDictionaries);
    log.info("운영 환경 카테고리 랭킹 사전 배포 완료: {}개", prodDictionaries.size());
  }

  private Sort createSort(String sortBy, String sortDir) {
    if (sortBy == null || sortBy.trim().isEmpty()) {
      sortBy = "updatedAt";
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
      log.warn("허용되지 않은 정렬 필드: {}. 기본값 updatedAt 사용", sortBy);
      sortBy = "updatedAt";
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

  @Override
  public String getDictionaryContent(DictionaryEnvironmentType environment) {
    List<CategoryRankingDictionary> dictionaries =
        repository.findByEnvironmentTypeOrderByKeywordAsc(environment);
    StringBuilder content = new StringBuilder();

    for (CategoryRankingDictionary dict : dictionaries) {
      content.append(dict.getKeyword()).append(": ");
      if (dict.getCategoryMappings() != null && !dict.getCategoryMappings().isEmpty()) {
        for (CategoryMapping mapping : dict.getCategoryMappings()) {
          content
              .append(mapping.getCategory())
              .append("(")
              .append(mapping.getWeight())
              .append("), ");
        }
        content.setLength(content.length() - 2); // 마지막 콤마 제거
      }
      if (dict.getDescription() != null && !dict.getDescription().isEmpty()) {
        content.append(" - ").append(dict.getDescription());
      }
      content.append("\n");
    }

    return content.toString();
  }

  @Override
  public void realtimeSync(DictionaryEnvironmentType environment) {
    log.info("카테고리 랭킹 사전 실시간 동기화 - 환경: {}", environment);
    // TODO: 캐시 업데이트 로직 구현
  }

  public String getDictionaryType() {
    return "CATEGORY_RANKING";
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

  @Transactional
  public void deleteByEnvironmentType(DictionaryEnvironmentType environment) {
    log.info("카테고리 랭킹 사전 환경별 삭제 시작 - 환경: {}", environment);
    repository.deleteByEnvironmentType(environment);
    log.info("카테고리 랭킹 사전 환경별 삭제 완료 - 환경: {}", environment);
  }
}
