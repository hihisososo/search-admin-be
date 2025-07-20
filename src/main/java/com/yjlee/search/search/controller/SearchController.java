package com.yjlee.search.search.controller;

import com.yjlee.search.search.dto.AutocompleteResponse;
import com.yjlee.search.search.dto.PriceRangeDto;
import com.yjlee.search.search.dto.ProductFiltersDto;
import com.yjlee.search.search.dto.ProductSortDto;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.service.SearchService;
import com.yjlee.search.searchlog.model.SearchLogDocument;
import com.yjlee.search.searchlog.service.SearchLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Search", description = "검색 API")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

  private final SearchService searchService;
  private final SearchLogService searchAnalyticsService;

  @Operation(summary = "상품 검색", description = "상품을 검색하고 필터링, 정렬, 집계 결과를 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "500", description = "검색 실행 실패")
  })
  @GetMapping
  public ResponseEntity<SearchExecuteResponse> search(
      @Parameter(description = "검색어", required = true) @RequestParam @NotBlank String query,
      @Parameter(description = "페이지 번호", required = true) @RequestParam @Min(1) Integer page,
      @Parameter(description = "페이지 크기", required = true) @RequestParam @Min(1) @Max(100)
          Integer size,
      @Parameter(description = "정렬 필드") @RequestParam(required = false, defaultValue = "score")
          String sortField,
      @Parameter(description = "정렬 순서") @RequestParam(required = false, defaultValue = "desc")
          String sortOrder,
      @Parameter(description = "브랜드 필터") @RequestParam(required = false) List<String> brand,
      @Parameter(description = "카테고리 필터") @RequestParam(required = false) List<String> category,
      @Parameter(description = "최소 가격") @RequestParam(required = false) Long priceFrom,
      @Parameter(description = "최대 가격") @RequestParam(required = false) Long priceTo,
      HttpServletRequest httpRequest) {

    SearchExecuteRequest request =
        buildSearchRequest(
            query, page, size, sortField, sortOrder, brand, category, priceFrom, priceTo);

    log.info(
        "상품 검색 요청 - 검색어: {}, 페이지: {}, 크기: {}",
        request.getQuery(),
        request.getPage(),
        request.getSize());

    LocalDateTime requestTime = LocalDateTime.now();
    String clientIp = getClientIp(httpRequest);
    String userAgent = getUserAgent(httpRequest);

    boolean isError = false;
    String errorMessage = null;
    SearchExecuteResponse response = null;
    long responseTimeMs = 0;

    try {
      long startTime = System.currentTimeMillis();

      response = searchService.searchProducts(request);
      responseTimeMs = System.currentTimeMillis() - startTime;

      log.info(
          "상품 검색 완료 - 소요시간: {}ms, 결과수: {}", responseTimeMs, response.getHits().getData().size());

    } catch (Exception e) {
      isError = true;
      errorMessage = e.getMessage();
      responseTimeMs =
          System.currentTimeMillis()
              - (requestTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

      log.error("상품 검색 실패 - 검색어: {}, 에러: {}", request.getQuery(), e.getMessage(), e);
      throw e;

    } finally {
      try {
        collectSearchLog(
            request,
            requestTime,
            clientIp,
            userAgent,
            responseTimeMs,
            response,
            isError,
            errorMessage);
      } catch (Exception logError) {
        log.warn("검색 로그 수집 실패: {}", logError.getMessage(), logError);
      }
    }

    return ResponseEntity.ok(response);
  }

  private void collectSearchLog(
      SearchExecuteRequest request,
      LocalDateTime timestamp,
      String clientIp,
      String userAgent,
      long responseTimeMs,
      SearchExecuteResponse response,
      boolean isError,
      String errorMessage) {

    String keyword =
        request.getQuery() != null && !request.getQuery().trim().isEmpty()
            ? request.getQuery().trim()
            : "unknown";

    Long resultCount =
        response != null && response.getHits() != null ? response.getHits().getTotal() : 0L;

    SearchLogDocument searchLog =
        SearchLogDocument.builder()
            .timestamp(timestamp)
            .searchKeyword(keyword)
            .indexName("products")
            .responseTimeMs(responseTimeMs)
            .resultCount(resultCount)
            .queryDsl("product_search")
            .clientIp(clientIp)
            .userAgent(userAgent)
            .isError(isError)
            .errorMessage(errorMessage)
            .build();

    searchAnalyticsService.saveSearchLog(searchLog);

    log.debug("검색 로그 수집 완료 - 키워드: {}, 결과수: {}", keyword, resultCount);
  }

  private String getClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }

    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      return xRealIp;
    }

    return request.getRemoteAddr();
  }

  @Operation(summary = "자동완성 검색", description = "키워드를 기반으로 autocomplete 인덱스에서 자동완성 결과를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "500", description = "자동완성 검색 실패")
  })
  @GetMapping("/autocomplete")
  public ResponseEntity<AutocompleteResponse> autocomplete(
      @Parameter(description = "검색 키워드", required = true)
          @RequestParam
          @NotBlank(message = "검색 키워드는 필수입니다")
          @Size(min = 1, max = 100, message = "검색 키워드는 1자 이상 100자 이하여야 합니다")
          String keyword) {

    log.info("자동완성 검색 요청 - 키워드: {}", keyword);

    try {
      AutocompleteResponse response = searchService.getAutocompleteSuggestions(keyword);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("자동완성 검색 실패 - 키워드: {}", keyword, e);
      throw e;
    }
  }

  private String getUserAgent(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    return userAgent != null ? userAgent : "unknown";
  }

  private SearchExecuteRequest buildSearchRequest(
      String query,
      Integer page,
      Integer size,
      String sortField,
      String sortOrder,
      List<String> brand,
      List<String> category,
      Long priceFrom,
      Long priceTo) {

    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery(query);
    request.setPage(page);
    request.setSize(size);

    // 정렬 설정
    if (sortField != null || sortOrder != null) {
      ProductSortDto sort = new ProductSortDto();
      sort.setField(sortField);
      sort.setOrder(sortOrder);
      request.setSort(sort);
    }

    // 필터 설정
    if (brand != null || category != null || priceFrom != null || priceTo != null) {
      ProductFiltersDto filters = new ProductFiltersDto();
      filters.setBrand(brand);
      filters.setCategory(category);

      if (priceFrom != null || priceTo != null) {
        PriceRangeDto priceRange = new PriceRangeDto();
        priceRange.setFrom(priceFrom);
        priceRange.setTo(priceTo);
        filters.setPriceRange(priceRange);
      }

      request.setFilters(filters);
    }

    return request;
  }
}
