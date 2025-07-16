package com.yjlee.search.dictionary.user.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryCreateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryListResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryResponse;
import com.yjlee.search.dictionary.user.dto.UserDictionaryUpdateRequest;
import com.yjlee.search.dictionary.user.dto.UserDictionaryVersionResponse;
import com.yjlee.search.dictionary.user.service.UserDictionaryService;
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

  private final UserDictionaryService userDictionaryService;

  @Operation(
      summary = "사용자 사전 목록 조회",
      description = "사용자 사전 목록을 페이징 및 검색어로 조회합니다. version 파라미터가 있으면 해당 버전의 배포된 사전을 조회합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping
  public ResponseEntity<PageResponse<UserDictionaryListResponse>> getUserDictionaries(
      @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "1") int page,
      @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "키워드 검색어") @RequestParam(required = false) String search,
      @Parameter(description = "정렬 필드 (keyword, updatedAt/deployedAt)")
          @RequestParam(defaultValue = "updatedAt")
          String sortBy,
      @Parameter(description = "정렬 방향 (asc, desc)") @RequestParam(defaultValue = "desc")
          String sortDir,
      @Parameter(description = "배포 버전 (없으면 현재 사전, 있으면 해당 버전의 배포된 사전)")
          @RequestParam(required = false)
          String version) {

    log.debug(
        "사용자 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, version: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        version);

    PageResponse<UserDictionaryListResponse> response =
        userDictionaryService.getUserDictionaries(page, size, search, sortBy, sortDir, version);
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
    UserDictionaryResponse response = userDictionaryService.getUserDictionaryDetail(dictionaryId);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "사용자 사전 생성", description = "새로운 사용자 사전을 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping
  public ResponseEntity<UserDictionaryResponse> createUserDictionary(
      @RequestBody @Valid UserDictionaryCreateRequest request) {

    log.debug("사용자 사전 생성 요청: {}", request.getKeyword());
    UserDictionaryResponse response = userDictionaryService.createUserDictionary(request);
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
      @RequestBody @Valid UserDictionaryUpdateRequest request) {

    log.debug("사용자 사전 수정 요청: {}", dictionaryId);
    UserDictionaryResponse response =
        userDictionaryService.updateUserDictionary(dictionaryId, request);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "사용자 사전 삭제", description = "사용자 사전을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @DeleteMapping("/{dictionaryId}")
  public ResponseEntity<Void> deleteUserDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId) {

    log.info("사용자 사전 삭제 요청: {}", dictionaryId);
    userDictionaryService.deleteUserDictionary(dictionaryId);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "사용자 사전 버전 생성",
      description = "현재 시점의 사용자 사전들을 스냅샷으로 저장하고 새 버전을 생성하며 EC2에 배포합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping("/versions")
  public ResponseEntity<UserDictionaryVersionResponse> createVersion() {

    log.info("사용자 사전 버전 생성 요청 (자동 버전)");
    UserDictionaryVersionResponse response = userDictionaryService.createVersion();
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "사용자 사전 버전 목록 조회", description = "사용자 사전의 모든 버전 목록을 조회합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping("/versions")
  public ResponseEntity<PageResponse<UserDictionaryVersionResponse>> getVersions(
      @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "1") int page,
      @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {

    log.debug("사용자 사전 버전 목록 조회 - page: {}, size: {}", page, size);
    PageResponse<UserDictionaryVersionResponse> response =
        userDictionaryService.getVersions(page, size);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "사용자 사전 버전 삭제", description = "특정 버전과 해당 버전의 모든 스냅샷을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 버전")
  })
  @DeleteMapping("/versions/{version}")
  public ResponseEntity<Void> deleteVersion(
      @Parameter(description = "삭제할 버전명") @PathVariable String version) {

    log.info("사용자 사전 버전 삭제 요청: {}", version);
    userDictionaryService.deleteVersion(version);
    return ResponseEntity.noContent().build();
  }
}
