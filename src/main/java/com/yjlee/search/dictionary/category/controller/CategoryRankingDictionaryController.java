package com.yjlee.search.dictionary.category.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.category.dto.*;
import com.yjlee.search.dictionary.category.service.CategoryRankingDictionaryService;
import com.yjlee.search.search.service.category.CategoryRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "카테고리 랭킹 사전")
@RestController
@RequestMapping("/api/v1/dictionaries/category-rankings")
@RequiredArgsConstructor
public class CategoryRankingDictionaryController {

  private final CategoryRankingDictionaryService service;
  private final CategoryRankingService categoryRankingService;

  @Operation(summary = "사전 목록")
  @GetMapping
  public PageResponse<CategoryRankingDictionaryListResponse> getList(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "updatedAt") String sortBy,
      @RequestParam(defaultValue = "desc") String sortDir,
      @RequestParam(required = false) EnvironmentType environment) {
    return service.getList(page, size, search, sortBy, sortDir, environment);
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
    return service.getCategories(environment);
  }

  @Operation(summary = "실시간 반영")
  @PostMapping("/realtime-sync")
  @ResponseStatus(HttpStatus.OK)
  public Map<String, Object> syncCategoryRankingDictionary(
      @RequestParam EnvironmentType environment) {
    categoryRankingService.updateCacheRealtime(environment);
    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("message", "카테고리 랭킹 사전 실시간 반영 완료");
    response.put("environment", environment.getDescription());
    response.put("timestamp", System.currentTimeMillis());
    return response;
  }
}
