package com.yjlee.search.dictionary.category.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.dto.*;
import com.yjlee.search.dictionary.category.service.CategoryRankingDictionaryService;
import com.yjlee.search.search.service.category.CategoryRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Category Ranking Dictionary Management", description = "카테고리 랭킹 사전 관리 API")
@RestController
@RequestMapping("/api/v1/dictionaries/category-rankings")
@RequiredArgsConstructor
public class CategoryRankingDictionaryController {

  private final CategoryRankingDictionaryService service;
  private final CategoryRankingService categoryRankingService;

  @Operation(summary = "카테고리 랭킹 사전 목록 조회", description = "카테고리 랭킹 사전 목록을 페이징 및 검색어로 조회합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping
  public ResponseEntity<PageResponse<CategoryRankingDictionaryListResponse>> getList(
      @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "키워드 검색어") @RequestParam(required = false) String search,
      @Parameter(description = "정렬 필드 (keyword, createdAt, updatedAt)")
          @RequestParam(defaultValue = "updatedAt")
          String sortBy,
      @Parameter(description = "정렬 방향 (asc, desc)") @RequestParam(defaultValue = "desc")
          String sortDir,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(required = false)
          DictionaryEnvironmentType environment) {

    log.debug(
        "카테고리 랭킹 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environment);

    PageResponse<CategoryRankingDictionaryListResponse> response =
        service.getList(page, size, search, sortBy, sortDir, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "카테고리 랭킹 사전 상세 조회", description = "특정 카테고리 랭킹 사전의 상세 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "404", description = "사전을 찾을 수 없음")
  })
  @GetMapping("/{id}")
  public ResponseEntity<CategoryRankingDictionaryResponse> getDetail(
      @Parameter(description = "사전 ID") @PathVariable Long id,
      @Parameter(description = "환경 타입") @RequestParam(required = false)
          DictionaryEnvironmentType environment) {

    log.debug("카테고리 랭킹 사전 상세 조회: {} - 환경: {}", id, environment);
    CategoryRankingDictionaryResponse response = service.getDetail(id, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "키워드로 카테고리 랭킹 사전 조회", description = "특정 키워드의 카테고리 매핑 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "404", description = "키워드를 찾을 수 없음")
  })
  @GetMapping("/by-keyword/{keyword}")
  public ResponseEntity<CategoryRankingDictionaryResponse> getByKeyword(
      @Parameter(description = "키워드") @PathVariable String keyword,
      @Parameter(description = "환경 타입") @RequestParam(required = false)
          DictionaryEnvironmentType environment) {

    log.debug("키워드로 카테고리 랭킹 사전 조회: {} - 환경: {}", keyword, environment);
    CategoryRankingDictionaryResponse response = service.getByKeyword(keyword, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "카테고리 랭킹 사전 생성", description = "새로운 카테고리 랭킹 사전을 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping
  public ResponseEntity<CategoryRankingDictionaryResponse> create(
      @RequestBody @Valid CategoryRankingDictionaryCreateRequest request,
      @Parameter(description = "환경 타입") @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("카테고리 랭킹 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);
    CategoryRankingDictionaryResponse response = service.create(request, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "카테고리 랭킹 사전 수정", description = "기존 카테고리 랭킹 사전을 수정합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "404", description = "사전을 찾을 수 없음")
  })
  @PutMapping("/{id}")
  public ResponseEntity<CategoryRankingDictionaryResponse> update(
      @Parameter(description = "사전 ID") @PathVariable Long id,
      @RequestBody @Valid CategoryRankingDictionaryUpdateRequest request,
      @Parameter(description = "환경 타입") @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("카테고리 랭킹 사전 수정 요청: {} - 환경: {}", id, environment);
    CategoryRankingDictionaryResponse response = service.update(id, request, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "카테고리 랭킹 사전 삭제", description = "카테고리 랭킹 사전을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(responseCode = "404", description = "사전을 찾을 수 없음")
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "사전 ID") @PathVariable Long id,
      @Parameter(description = "환경 타입") @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.info("카테고리 랭킹 사전 삭제 요청: {} - 환경: {}", id, environment);
    service.delete(id, environment);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "전체 카테고리 목록 조회", description = "등록된 모든 유니크한 카테고리 목록을 조회합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping("/categories")
  public ResponseEntity<CategoryListResponse> getCategories(
      @Parameter(description = "환경 타입") @RequestParam(required = false)
          DictionaryEnvironmentType environment) {

    log.debug("전체 카테고리 목록 조회 - 환경: {}", environment);
    CategoryListResponse response = service.getCategories(environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "카테고리 랭킹 사전 실시간 반영", description = "카테고리 랭킹 사전 변경사항을 검색 캐시에 즉시 반영")
  @PostMapping("/realtime-sync")
  public ResponseEntity<Map<String, Object>> syncCategoryRankingDictionary(
      @Parameter(description = "환경 타입 (CURRENT/DEV/PROD)", example = "DEV") @RequestParam
          DictionaryEnvironmentType environment) {

    log.info("카테고리 랭킹 사전 실시간 반영 요청 - 환경: {}", environment.getDescription());
    categoryRankingService.updateCacheRealtime(environment);

    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("message", "카테고리 랭킹 사전 실시간 반영 완료");
    response.put("environment", environment.getDescription());
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "카테고리 랭킹 사전 동기화 상태 조회", description = "카테고리 랭킹 사전의 동기화 상태 조회")
  @GetMapping("/sync-status")
  public ResponseEntity<Map<String, Object>> getCategoryRankingSyncStatus() {

    log.info("카테고리 랭킹 사전 동기화 상태 조회 요청");
    String cacheStatus = categoryRankingService.getCacheStatus();

    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("cacheStatus", cacheStatus);
    response.put("lastSyncTime", System.currentTimeMillis());
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.ok(response);
  }
}
