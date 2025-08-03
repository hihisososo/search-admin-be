package com.yjlee.search.dictionary.user.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryListResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.service.UserDictionaryServiceV2;
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
@Tag(name = "User Dictionary Management", description = "사용자 사전 관리 API")
@RestController
@RequestMapping("/api/v1/dictionaries/user")
@RequiredArgsConstructor
public class UserDictionaryController {

  private final UserDictionaryServiceV2 userDictionaryService;

  @Operation(
      summary = "사용자 사전 목록 조회",
      description = "사용자 사전 목록을 페이징 및 검색어로 조회합니다. environment 파라미터로 현재/개발/운영 환경의 사전을 조회할 수 있습니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping
  public ResponseEntity<PageResponse<UserDictionaryListResponse>> getUserDictionaries(
      @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "1") int page,
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
        "사용자 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, environment: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        environment);

    PageResponse<UserDictionaryListResponse> response =
        userDictionaryService.getList(page - 1, size, sortBy, sortDir, search, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "사용자 사전 상세 조회", description = "특정 사용자 사전의 상세 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @GetMapping("/{dictionaryId}")
  public ResponseEntity<UserDictionaryResponse> getUserDictionaryDetail(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId) {

    log.debug("사용자 사전 상세 조회: {}", dictionaryId);
    UserDictionaryResponse response =
        userDictionaryService.get(dictionaryId, DictionaryEnvironmentType.CURRENT);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "사용자 사전 생성", description = "새로운 사용자 사전을 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping
  public ResponseEntity<UserDictionaryResponse> createUserDictionary(
      @RequestBody @Valid UserDictionaryCreateRequest request,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("사용자 사전 생성 요청: {} - 환경: {}", request.getKeyword(), environment);
    UserDictionaryResponse response = userDictionaryService.create(request, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "사용자 사전 수정", description = "기존 사용자 사전을 수정합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PutMapping("/{dictionaryId}")
  public ResponseEntity<UserDictionaryResponse> updateUserDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId,
      @RequestBody @Valid UserDictionaryUpdateRequest request,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.debug("사용자 사전 수정 요청: {} - 환경: {}", dictionaryId, environment);
    UserDictionaryResponse response =
        userDictionaryService.update(dictionaryId, request, environment);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "사용자 사전 삭제", description = "사용자 사전을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @DeleteMapping("/{dictionaryId}")
  public ResponseEntity<Void> deleteUserDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId,
      @Parameter(description = "환경 타입 (CURRENT: 현재, DEV: 개발, PROD: 운영)")
          @RequestParam(defaultValue = "CURRENT")
          DictionaryEnvironmentType environment) {

    log.info("사용자 사전 삭제 요청: {} - 환경: {}", dictionaryId, environment);
    userDictionaryService.delete(dictionaryId, environment);
    return ResponseEntity.noContent().build();
  }
}
