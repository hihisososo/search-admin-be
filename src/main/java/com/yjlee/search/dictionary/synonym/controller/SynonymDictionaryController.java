package com.yjlee.search.dictionary.synonym.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryCreateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryListResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryResponse;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryUpdateRequest;
import com.yjlee.search.dictionary.synonym.dto.SynonymDictionaryVersionResponse;
import com.yjlee.search.dictionary.synonym.service.SynonymDictionaryService;
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
@Tag(name = "Synonym Dictionary Management", description = "유의어 사전 관리 API")
@RestController
@RequestMapping("/api/v1/dictionaries/synonym")
@RequiredArgsConstructor
public class SynonymDictionaryController {

  private final SynonymDictionaryService synonymDictionaryService;

  @Operation(
      summary = "유의어 사전 목록 조회",
      description = "유의어 사전 목록을 페이징 및 검색어로 조회합니다. version 파라미터가 있으면 해당 버전의 배포된 사전을 조회합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping
  public ResponseEntity<PageResponse<SynonymDictionaryListResponse>> getSynonymDictionaries(
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
        "유의어 사전 목록 조회 - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}, version: {}",
        page,
        size,
        search,
        sortBy,
        sortDir,
        version);

    PageResponse<SynonymDictionaryListResponse> response =
        synonymDictionaryService.getSynonymDictionaries(
            page, size, search, sortBy, sortDir, version);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "유의어 사전 상세 조회", description = "특정 유의어 사전의 상세 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @GetMapping("/{dictionaryId}")
  public ResponseEntity<SynonymDictionaryResponse> getSynonymDictionaryDetail(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId) {

    log.debug("유의어 사전 상세 조회: {}", dictionaryId);
    SynonymDictionaryResponse response =
        synonymDictionaryService.getSynonymDictionaryDetail(dictionaryId);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "유의어 사전 생성", description = "새로운 유의어 사전을 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping
  public ResponseEntity<SynonymDictionaryResponse> createSynonymDictionary(
      @RequestBody @Valid SynonymDictionaryCreateRequest request) {

    log.debug("유의어 사전 생성 요청: {}", request.getKeyword());
    SynonymDictionaryResponse response = synonymDictionaryService.createSynonymDictionary(request);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "유의어 사전 수정", description = "기존 유의어 사전을 수정합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PutMapping("/{dictionaryId}")
  public ResponseEntity<SynonymDictionaryResponse> updateSynonymDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId,
      @RequestBody @Valid SynonymDictionaryUpdateRequest request) {

    log.debug("유의어 사전 수정 요청: {}", dictionaryId);
    SynonymDictionaryResponse response =
        synonymDictionaryService.updateSynonymDictionary(dictionaryId, request);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "유의어 사전 삭제", description = "유의어 사전을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 사전")
  })
  @DeleteMapping("/{dictionaryId}")
  public ResponseEntity<Void> deleteSynonymDictionary(
      @Parameter(description = "사전 ID") @PathVariable Long dictionaryId) {

    log.info("유의어 사전 삭제 요청: {}", dictionaryId);
    synonymDictionaryService.deleteSynonymDictionary(dictionaryId);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "유의어 사전 버전 생성",
      description = "현재 시점의 유의어 사전들을 스냅샷으로 저장하고 새 버전을 생성하며 EC2에 배포합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping("/versions")
  public ResponseEntity<SynonymDictionaryVersionResponse> createVersion() {

    log.info("유의어 사전 버전 생성 요청 (자동 버전)");
    SynonymDictionaryVersionResponse response = synonymDictionaryService.createVersion();
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "유의어 사전 버전 목록 조회", description = "유의어 사전의 모든 버전 목록을 조회합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping("/versions")
  public ResponseEntity<PageResponse<SynonymDictionaryVersionResponse>> getVersions(
      @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "1") int page,
      @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {

    log.debug("유의어 사전 버전 목록 조회 - page: {}, size: {}", page, size);
    PageResponse<SynonymDictionaryVersionResponse> response =
        synonymDictionaryService.getVersions(page, size);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "유의어 사전 버전 삭제", description = "특정 버전과 해당 버전의 모든 스냅샷을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 버전")
  })
  @DeleteMapping("/versions/{version}")
  public ResponseEntity<Void> deleteVersion(
      @Parameter(description = "삭제할 버전명") @PathVariable String version) {

    log.info("유의어 사전 버전 삭제 요청: {}", version);
    synonymDictionaryService.deleteVersion(version);
    return ResponseEntity.noContent().build();
  }
}
