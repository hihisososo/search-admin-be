package com.yjlee.search.search.controller;

import com.yjlee.search.common.PageResponse;
import com.yjlee.search.search.dto.*;
import com.yjlee.search.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Search Simulation", description = "검색 시뮬레이션 API")
@RestController
@RequestMapping("/api/v1/search-simulation")
@RequiredArgsConstructor
public class SearchController {

  private final SearchService searchService;

  @Operation(summary = "인덱스 목록 조회", description = "검색 가능한 인덱스 목록을 조회합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping("/indexes")
  public ResponseEntity<List<IndexNameResponse>> getIndexList() {
    log.debug("인덱스 목록 조회 요청");

    List<IndexNameResponse> indexes = searchService.getIndexList();
    return ResponseEntity.ok(indexes);
  }

  @Operation(summary = "검색 실행", description = "Query DSL을 사용하여 지정된 인덱스에서 검색을 실행합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping("/execute")
  public ResponseEntity<SearchExecuteResponse> executeSearch(
      @RequestBody @Valid SearchExecuteRequest request) {

    log.info("검색 실행 요청 - 인덱스: {}", request.getIndexName());

    SearchExecuteResponse response = searchService.executeSearch(request);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "검색식 목록 조회", description = "저장된 검색식 목록을 페이징 및 검색어로 조회합니다.")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "성공")})
  @GetMapping("/queries")
  public ResponseEntity<PageResponse<SearchQueryListResponse>> getSearchQueries(
      @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "1") int page,
      @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "검색식 이름 검색어") @RequestParam(required = false) String search,
      @Parameter(description = "인덱스명 필터") @RequestParam(required = false) String indexName,
      @Parameter(description = "정렬 필드 (name, indexName, createdAt, updatedAt)")
          @RequestParam(defaultValue = "updatedAt")
          String sortBy,
      @Parameter(description = "정렬 방향 (asc, desc)") @RequestParam(defaultValue = "desc")
          String sortDir) {

    log.debug(
        "검색식 목록 조회 - page: {}, size: {}, search: {}, indexName: {}, sortBy: {}, sortDir: {}",
        page,
        size,
        search,
        indexName,
        sortBy,
        sortDir);

    PageResponse<SearchQueryListResponse> response =
        searchService.getSearchQueries(page, size, search, indexName, sortBy, sortDir);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "검색식 상세 조회", description = "특정 검색식의 상세 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 검색식")
  })
  @GetMapping("/queries/{queryId}")
  public ResponseEntity<SearchQueryResponse> getSearchQueryDetail(
      @Parameter(description = "검색식 ID") @PathVariable Long queryId) {

    log.debug("검색식 상세 조회: {}", queryId);
    SearchQueryResponse response = searchService.getSearchQueryDetail(queryId);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "검색식 생성", description = "새로운 검색식을 저장합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping("/queries")
  public ResponseEntity<SearchQueryResponse> createSearchQuery(
      @RequestBody @Valid SearchQueryCreateRequest request) {

    log.debug("검색식 생성 요청: {}", request.getName());
    SearchQueryResponse response = searchService.createSearchQuery(request);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "검색식 수정", description = "기존 검색식을 수정합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PutMapping("/queries/{queryId}")
  public ResponseEntity<SearchQueryResponse> updateSearchQuery(
      @Parameter(description = "검색식 ID") @PathVariable Long queryId,
      @RequestBody @Valid SearchQueryUpdateRequest request) {

    log.debug("검색식 수정 요청: {}", queryId);
    SearchQueryResponse response = searchService.updateSearchQuery(queryId, request);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "검색식 삭제", description = "검색식을 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(responseCode = "400", description = "존재하지 않는 검색식")
  })
  @DeleteMapping("/queries/{queryId}")
  public ResponseEntity<Void> deleteSearchQuery(
      @Parameter(description = "검색식 ID") @PathVariable Long queryId) {

    log.info("검색식 삭제 요청: {}", queryId);
    searchService.deleteSearchQuery(queryId);
    return ResponseEntity.noContent().build();
  }
}
