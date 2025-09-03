package com.yjlee.search.dictionary.stopword.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryCreateRequest;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryListResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryResponse;
import com.yjlee.search.dictionary.stopword.dto.StopwordDictionaryUpdateRequest;
import com.yjlee.search.dictionary.stopword.service.StopwordDictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Stopword Dictionary Management", description = "불용어 사전 관리 API")
@RestController
@RequestMapping("/api/v1/dictionaries/stopwords")
@RequiredArgsConstructor
public class StopwordDictionaryController {

  private final StopwordDictionaryService stopwordDictionaryService;

  @Operation(
      summary = "불용어 사전 목록 조회",
      description = "불용어 사전 목록을 페이징 및 검색어로 조회합니다. environment 파라미터로 현재/개발/운영 환경의 사전을 조회할 수 있습니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping
  public ResponseEntity<PageResponse<StopwordDictionaryListResponse>> getStopwordDictionaries(
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
        "불용어 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environment);

    PageResponse<StopwordDictionaryListResponse> response =
        stopwordDictionaryService.getList(page, size, sortBy, sortDir, search, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "불용어 사전 상세 조회", description = "특정 불용어 사전의 상세 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @GetMapping("/{dictionaryId}")
  public ResponseEntity<StopwordDictionaryResponse> getStopwordDictionaryDetail(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId) {

    log.debug("불용어 사전 상세 조회: {}", dictionaryId);
    StopwordDictionaryResponse response =
        stopwordDictionaryService.get(dictionaryId, DictionaryEnvironmentType.CURRENT);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "불용어 사전 생성", description = "새로운 불용어 사전을 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping
  public ResponseEntity<StopwordDictionaryResponse> createStopwordDictionary(
      @RequestBody @Valid StopwordDictionaryCreateRequest request,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("불용어 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);
    StopwordDictionaryResponse response = stopwordDictionaryService.create(request, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "불용어 사전 수정", description = "기존 불용어 사전을 수정합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PutMapping("/{dictionaryId}")
  public ResponseEntity<StopwordDictionaryResponse> updateStopwordDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId,
      @RequestBody @Valid StopwordDictionaryUpdateRequest request,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("불용어 사전 수정 요청: {} - 환경: {}", dictionaryId, environment);
    StopwordDictionaryResponse response =
        stopwordDictionaryService.update(dictionaryId, request, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "불용어 사전 삭제", description = "불용어 사전을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @DeleteMapping("/{dictionaryId}")
  public ResponseEntity<Void> deleteStopwordDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.info("불용어 사전 삭제 요청: {} - 환경: {}", dictionaryId, environment);
    stopwordDictionaryService.delete(dictionaryId, environment);
    return ResponseEntity.noContent().build();
  }
}
