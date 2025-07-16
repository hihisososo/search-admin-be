package com.yjlee.search.index.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.index.dto.*;
import com.yjlee.search.index.service.IndexService;
import com.yjlee.search.service.S3FileService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Tag(name = "Index Management", description = "검색 색인 관리 API")
@RestController
@RequestMapping("/api/v1/indexes")
@RequiredArgsConstructor
public class IndexController {
  private final IndexService indexService;
  private final S3FileService s3FileService;

  @Operation(summary = "색인 목록 조회", description = "색인 목록을 페이징 및 검색어로 조회합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping
  public ResponseEntity<PageResponse<IndexListResponse>> getIndexes(
      @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "1") int page,
      @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "색인명 검색어") @RequestParam(required = false) String search,
      @Parameter(description = "정렬 필드 (name, lastIndexedAt, createdAt, updatedAt)")
          @RequestParam(defaultValue = "updatedAt")
          String sortBy,
      @Parameter(description = "정렬 방향 (asc, desc)") @RequestParam(defaultValue = "desc")
          String sortDir) {
    log.debug(
        "Getting indexes - page: {}, size: {}, search: {}, sortBy: {}, sortDir: {}",
        page,
        size,
        search,
        sortBy,
        sortDir);
    PageResponse<IndexListResponse> res =
        indexService.getIndexes(page, size, search, sortBy, sortDir);
    return ResponseEntity.ok(res);
  }

  @Operation(summary = "색인 상세 조회", description = "특정 색인의 상세 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 색인")
  })
  @GetMapping("/{indexId}")
  public ResponseEntity<IndexResponse> getIndexDetail(
      @Parameter(description = "색인 ID") @PathVariable Long indexId) {
    IndexResponse indexDetail = indexService.getIndexDetail(indexId);
    return ResponseEntity.ok(indexDetail);
  }

  @Operation(summary = "색인 추가", description = "색인을 추가합니다. dataSource가 'json'이면 파일 업로드 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<IndexResponse> createIndex(
      @RequestPart(value = "dto") @Valid IndexCreateRequest dto,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    return ResponseEntity.ok(indexService.createIndex(dto, file));
  }

  @Operation(summary = "색인 업데이트", description = "색인의 데이터 설정을 업데이트합니다. 파일 변경 시 기존 파일 삭제 후 새 파일 업로드.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PutMapping(
      value = "/{indexId}",
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<IndexResponse> updateIndex(
      @Parameter(description = "색인 ID") @PathVariable Long indexId,
      @RequestPart(value = "dto") @Valid IndexUpdateRequest dto,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    log.debug("Updating index: {} with data: {}", indexId, dto);
    return ResponseEntity.ok(indexService.updateIndex(indexId, dto, file));
  }

  @Operation(summary = "색인 삭제", description = "색인을 삭제합니다. 관련 파일, DB 메타데이터, ES 인덱스를 모두 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 색인")
  })
  @DeleteMapping("/{indexId}")
  public ResponseEntity<Void> deleteIndex(
      @Parameter(description = "색인 ID") @PathVariable String indexId) {
    log.info("색인 삭제 요청: {}", indexId);
    Long id = Long.parseLong(indexId);
    indexService.deleteIndex(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "JSON 파일 다운로드용 Presigned URL 생성",
      description = "색인의 JSON 파일을 다운로드하기 위한 Presigned URL을 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "JSON 데이터 소스가 아니거나 파일이 없음")
  })
  @GetMapping("/{indexId}/download")
  public ResponseEntity<IndexDownloadResponse> generateFileDownloadUrl(
      @Parameter(description = "색인 ID") @PathVariable Long indexId) {
    log.info("JSON 파일 다운로드 URL 생성 요청: {}", indexId);
    return ResponseEntity.ok(indexService.generateFileDownloadUrl(indexId));
  }

  @Operation(summary = "색인 실행", description = "JSON 파일의 데이터를 Elasticsearch에 색인합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "JSON 데이터 소스가 아니거나 파일이 없음")
  })
  @PostMapping("/{indexId}/run")
  public ResponseEntity<Void> runIndex(
      @Parameter(description = "색인 ID") @PathVariable String indexId) {
    log.info("색인 실행 요청: {}", indexId);
    Long id = Long.parseLong(indexId);
    indexService.runIndex(id);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "색인명 중복 체크", description = "색인명이 이미 존재하는지 확인합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @GetMapping("/check-name")
  public ResponseEntity<Map<String, Object>> checkIndexName(
      @Parameter(description = "확인할 색인명") @RequestParam String name) {

    log.debug("색인명 중복 체크 요청: {}", name);

    // 색인명 검증
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("색인명이 필요합니다");
    }

    boolean exists = indexService.checkIndexNameExists(name.trim());

    Map<String, Object> result = new HashMap<>();
    result.put("name", name.trim());
    result.put("exists", exists);
    result.put("message", exists ? "이미 존재하는 색인명입니다" : "사용 가능한 색인명입니다");

    return ResponseEntity.ok(result);
  }
}
