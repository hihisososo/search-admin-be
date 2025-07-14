package com.yjlee.search.index.controller;

import com.yjlee.search.index.dto.IndexRequest;
import com.yjlee.search.index.dto.IndexResponse;
import com.yjlee.search.index.service.IndexService;
import com.yjlee.search.service.S3FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
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
  public ResponseEntity<Map<String, Object>> getIndexes(
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
    List<IndexResponse> items = indexService.getIndexes(page, size, search, sortBy, sortDir);
    int total = indexService.getTotalCount(search);
    Map<String, Object> result = new HashMap<>();
    result.put("items", items);
    result.put("total", total);
    result.put("page", page);
    result.put("size", size);
    return ResponseEntity.ok(result);
  }

  @Operation(summary = "색인 추가", description = "색인을 추가합니다. dataSource가 'json'이면 파일 업로드 필요.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<Map<String, Object>> addIndex(
      @RequestPart(value = "dto", required = true) @Valid IndexRequest dto,
      @RequestPart(value = "file", required = false) MultipartFile file) {

    String id = indexService.addIndex(dto, file);
    return ResponseEntity.ok(indexService.buildAddIndexResponse(id, file));
  }

  @Operation(
      summary = "파일 다운로드용 Presigned URL 생성",
      description = "S3에 저장된 JSON 파일을 다운로드하기 위한 Presigned URL을 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @GetMapping("/{indexName}/download-url")
  public ResponseEntity<Map<String, Object>> generateDownloadUrl(
      @Parameter(description = "색인명") @PathVariable String indexName,
      @Parameter(description = "S3 키 또는 파일 URL") @RequestParam String s3Key) {

    log.info("Generating download presigned URL - indexName: {}, s3Key: {}", indexName, s3Key);

    String actualS3Key =
        s3Key.startsWith("http") ? s3FileService.extractS3KeyFromUrl(s3Key) : s3Key;

    if (actualS3Key == null) {
      throw new IllegalArgumentException("유효하지 않은 S3 키 또는 URL입니다");
    }

    // Presigned URL 생성
    String presignedUrl = s3FileService.generateDownloadPresignedUrl(actualS3Key);

    Map<String, Object> result = new HashMap<>();
    result.put("presignedUrl", presignedUrl);
    result.put("s3Key", actualS3Key);
    result.put("indexName", indexName);
    result.put("expiresInHours", 1);

    return ResponseEntity.ok(result);
  }
}
