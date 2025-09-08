package com.yjlee.search.dictionary.unit.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryCreateRequest;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryListResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryResponse;
import com.yjlee.search.dictionary.unit.dto.UnitDictionaryUpdateRequest;
import com.yjlee.search.dictionary.unit.service.UnitDictionaryService;
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
@Tag(name = "Unit Dictionary Management", description = "단위 사전 관리 API")
@RestController
@RequestMapping("/api/v1/dictionaries/units")
@RequiredArgsConstructor
public class UnitDictionaryController {

  private final UnitDictionaryService unitDictionaryService;

  @Operation(
      summary = "단위 사전 목록 조회",
      description = "단위 사전 목록을 페이징 및 검색어로 조회합니다. environment 파라미터로 현재/개발/운영 환경의 사전을 조회할 수 있습니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping
  public ResponseEntity<PageResponse<UnitDictionaryListResponse>> getUnitDictionaries(
      @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size,
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
        "단위 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environment);

    PageResponse<UnitDictionaryListResponse> response =
        unitDictionaryService.getList(page, size, sortBy, sortDir, search, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "단위 사전 상세 조회", description = "특정 단위 사전의 상세 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @GetMapping("/{dictionaryId}")
  public ResponseEntity<UnitDictionaryResponse> getUnitDictionaryDetail(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId) {

    log.debug("단위 사전 상세 조회: {}", dictionaryId);
    UnitDictionaryResponse response =
        unitDictionaryService.get(dictionaryId, DictionaryEnvironmentType.CURRENT);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "단위 사전 생성", description = "새로운 단위 사전을 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping
  public ResponseEntity<UnitDictionaryResponse> createUnitDictionary(
      @RequestBody @Valid UnitDictionaryCreateRequest request,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("단위 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);
    UnitDictionaryResponse response = unitDictionaryService.create(request, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "단위 사전 수정", description = "기존 단위 사전을 수정합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PutMapping("/{dictionaryId}")
  public ResponseEntity<UnitDictionaryResponse> updateUnitDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId,
      @RequestBody @Valid UnitDictionaryUpdateRequest request,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("단위 사전 수정 요청: {} - 환경: {}", dictionaryId, environment);
    UnitDictionaryResponse response =
        unitDictionaryService.update(dictionaryId, request, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "단위 사전 삭제", description = "단위 사전을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @DeleteMapping("/{dictionaryId}")
  public ResponseEntity<Void> deleteUnitDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.info("단위 사전 삭제 요청: {} - 환경: {}", dictionaryId, environment);
    unitDictionaryService.delete(dictionaryId, environment);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "단위 사전 실시간 반영", description = "단위 사전은 실시간 반영이 불가능합니다. 인덱스를 재생성해야 합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "400", description = "실시간 반영 불가"),
    @ApiResponse(responseCode = "500", description = "서버 에러")
  })
  @PostMapping("/realtime-sync")
  public ResponseEntity<Map<String, Object>> syncUnitDictionary(
      @Parameter(description = "환경 타입 (CURRENT/DEV/PROD)", example = "DEV") @RequestParam
          DictionaryEnvironmentType environment) {

    log.info("단위 사전 실시간 반영 요청 - 환경: {}", environment.getDescription());

    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("success", false);
    errorResponse.put("message", "단위 사전은 실시간 반영이 불가능합니다. 인덱스를 재생성해야 적용됩니다.");
    errorResponse.put("environment", environment.getDescription());

    return ResponseEntity.badRequest().body(errorResponse);
  }
}
