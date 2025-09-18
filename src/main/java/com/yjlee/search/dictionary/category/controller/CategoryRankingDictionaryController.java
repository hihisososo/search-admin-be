package com.yjlee.search.dictionary.category.controller;

import com.yjlee.search.common.dto.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.category.dto.*;
import com.yjlee.search.dictionary.category.service.CategoryRankingDictionaryService;
import com.yjlee.search.dictionary.category.service.ProductCategoryService;
import com.yjlee.search.search.service.category.CategoryRankingCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "카테고리 랭킹 사전")
@RestController
@RequestMapping("/api/v1/dictionaries/category-rankings")
@RequiredArgsConstructor
public class CategoryRankingDictionaryController {

  private final CategoryRankingDictionaryService service;
  private final ProductCategoryService productCategoryService;
  private final CategoryRankingCacheService categoryRankingCacheService;

  @Operation(summary = "사전 목록")
  @GetMapping
  public PageResponse<CategoryRankingDictionaryListResponse> getList(
      @ParameterObject
          @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) EnvironmentType environment) {
    return service.getList(pageable, search, environment);
  }

  @Operation(summary = "사전 상세")
  @GetMapping("/{id}")
  public CategoryRankingDictionaryResponse getDetail(@PathVariable Long id) {
    return service.getDetail(id);
  }

  @Operation(summary = "사전 생성")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CategoryRankingDictionaryResponse create(
      @RequestBody @Valid CategoryRankingDictionaryCreateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return service.create(request, environment);
  }

  @Operation(summary = "사전 수정")
  @PutMapping("/{id}")
  public CategoryRankingDictionaryResponse update(
      @PathVariable Long id,
      @RequestBody @Valid CategoryRankingDictionaryUpdateRequest request,
      @RequestParam(defaultValue = "CURRENT") EnvironmentType environment) {
    return service.update(id, request, environment);
  }

  @Operation(summary = "사전 삭제")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }

  @Operation(summary = "카테고리 목록")
  @GetMapping("/categories")
  public CategoryListResponse getCategories(
      @RequestParam(required = false) EnvironmentType environment) {
    return productCategoryService.getCategories();
  }

  @Operation(summary = "실시간 반영")
  @PostMapping("/realtime-sync")
  @ResponseStatus(HttpStatus.OK)
  public Map<String, Object> syncCategoryRankingDictionary(
      @RequestParam EnvironmentType environment) {
    categoryRankingCacheService.refreshCache(environment);
    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    return response;
  }
}
