package com.yjlee.search.dictionary.typo.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryCreateRequest;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryListResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryResponse;
import com.yjlee.search.dictionary.typo.dto.TypoCorrectionDictionaryUpdateRequest;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import com.yjlee.search.search.service.SearchService;
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
@Tag(name = "Typo Correction Dictionary Management", description = "오타교정 사전 관리 API")
@RestController
@RequestMapping("/api/v1/dictionaries/typos")
@RequiredArgsConstructor
public class TypoCorrectionDictionaryController {

  private final TypoCorrectionDictionaryService typoCorrectionDictionaryService;
  private final SearchService searchService;

  @Operation(
      summary = "오타교정 사전 목록 조회",
      description = "오타교정 사전 목록을 페이징 및 검색어로 조회합니다. environment 파라미터로 현재/개발/운영 환경의 사전을 조회할 수 있습니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping
  public ResponseEntity<PageResponse<TypoCorrectionDictionaryListResponse>>
      getTypoCorrectionDictionaries(
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
        "오타교정 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environment);

    PageResponse<TypoCorrectionDictionaryListResponse> response =
        typoCorrectionDictionaryService.getTypoCorrectionDictionaries(
            page, size, search, sortBy, sortDir, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "오타교정 사전 상세 조회", description = "특정 오타교정 사전의 상세 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @GetMapping("/{dictionaryId}")
  public ResponseEntity<TypoCorrectionDictionaryResponse> getTypoCorrectionDictionaryDetail(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("오타교정 사전 상세 조회: {} - 환경: {}", dictionaryId, environment);
    TypoCorrectionDictionaryResponse response =
        typoCorrectionDictionaryService.getTypoCorrectionDictionaryDetail(
            dictionaryId, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "오타교정 사전 생성", description = "새로운 오타교정 사전을 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping
  public ResponseEntity<TypoCorrectionDictionaryResponse> createTypoCorrectionDictionary(
      @RequestBody @Valid TypoCorrectionDictionaryCreateRequest request,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("오타교정 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);
    TypoCorrectionDictionaryResponse response =
        typoCorrectionDictionaryService.createTypoCorrectionDictionary(request, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "오타교정 사전 수정", description = "기존 오타교정 사전을 수정합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PutMapping("/{dictionaryId}")
  public ResponseEntity<TypoCorrectionDictionaryResponse> updateTypoCorrectionDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId,
      @RequestBody @Valid TypoCorrectionDictionaryUpdateRequest request,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("오타교정 사전 수정 요청: {} - 환경: {}", dictionaryId, environment);
    TypoCorrectionDictionaryResponse response =
        typoCorrectionDictionaryService.updateTypoCorrectionDictionary(
            dictionaryId, request, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "오타교정 사전 삭제", description = "오타교정 사전을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @DeleteMapping("/{dictionaryId}")
  public ResponseEntity<Void> deleteTypoCorrectionDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.info("오타교정 사전 삭제 요청: {} - 환경: {}", dictionaryId, environment);
    typoCorrectionDictionaryService.deleteTypoCorrectionDictionary(dictionaryId, environment);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "오타교정 사전 실시간 반영", description = "오타교정 사전 변경사항을 검색 캐시에 즉시 반영")
  @PostMapping("/realtime-sync")
  public ResponseEntity<Map<String, Object>> syncTypoCorrectionDictionary(
      @Parameter(description = "환경 타입 (CURRENT/DEV/PROD)", example = "DEV") @RequestParam
          DictionaryEnvironmentType environment) {

    log.info("오타교정 사전 실시간 반영 요청 - 환경: {}", environment.getDescription());
    searchService.updateTypoCorrectionCacheRealtime(environment);

    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("message", "오타교정 사전 실시간 반영 완료");
    response.put("environment", environment.getDescription());
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.ok(response);
  }
}
