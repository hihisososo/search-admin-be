package com.yjlee.search.search.controller;

import com.yjlee.search.common.util.HttpRequestUtils;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.search.dto.AutocompleteResponse;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchSimulationRequest;
import com.yjlee.search.search.service.SearchRequestBuilderService;
import com.yjlee.search.search.service.SearchService;
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
  private final SearchLogService searchLogService;
  private final SearchRequestBuilderService searchRequestBuilderService;
  private final HttpRequestUtils httpRequestUtils;

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
        searchRequestBuilderService.buildSearchRequest(
            query, page, size, sortField, sortOrder, brand, category, priceFrom, priceTo);

    log.info(
        "상품 검색 요청 - 검색어: {}, 페이지: {}, 크기: {}",
        request.getQuery(),
        request.getPage(),
        request.getSize());

    LocalDateTime requestTime = LocalDateTime.now();
    String clientIp = httpRequestUtils.getClientIp(httpRequest);
    String userAgent = httpRequestUtils.getUserAgent(httpRequest);

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
        searchLogService.collectSearchLog(
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

  @Operation(
      summary = "상품 검색 시뮬레이션",
      description = "개발/운영 환경을 선택하여 상품을 검색하고 필터링, 정렬, 집계 결과를 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "500", description = "검색 실행 실패")
  })
  @GetMapping("/simulation")
  public ResponseEntity<SearchExecuteResponse> searchSimulation(
      @Parameter(description = "환경 타입 (DEV: 개발, PROD: 운영)", required = true) @RequestParam
          IndexEnvironment.EnvironmentType environmentType,
      @Parameter(description = "검색어", required = true) @RequestParam @NotBlank String query,
      @Parameter(description = "페이지 번호", required = true) @RequestParam @Min(1) Integer page,
      @Parameter(description = "페이지 크기", required = true) @RequestParam @Min(1) @Max(100)
          Integer size,
      @Parameter(description = "Elasticsearch explain 결과 포함 여부")
          @RequestParam(required = false, defaultValue = "false")
          boolean explain,
      @Parameter(description = "정렬 필드") @RequestParam(required = false, defaultValue = "score")
          String sortField,
      @Parameter(description = "정렬 순서") @RequestParam(required = false, defaultValue = "desc")
          String sortOrder,
      @Parameter(description = "브랜드 필터") @RequestParam(required = false) List<String> brand,
      @Parameter(description = "카테고리 필터") @RequestParam(required = false) List<String> category,
      @Parameter(description = "최소 가격") @RequestParam(required = false) Long priceFrom,
      @Parameter(description = "최대 가격") @RequestParam(required = false) Long priceTo,
      HttpServletRequest httpRequest) {

    SearchSimulationRequest request =
        searchRequestBuilderService.buildSearchSimulationRequest(
            environmentType,
            query,
            page,
            size,
            explain,
            sortField,
            sortOrder,
            brand,
            category,
            priceFrom,
            priceTo);

    log.info(
        "상품 검색 시뮬레이션 요청 - 환경: {}, 검색어: {}, 페이지: {}, 크기: {}, explain: {}",
        request.getEnvironmentType().getDescription(),
        request.getQuery(),
        request.getPage(),
        request.getSize(),
        request.isExplain());

    LocalDateTime requestTime = LocalDateTime.now();
    String clientIp = httpRequestUtils.getClientIp(httpRequest);
    String userAgent = httpRequestUtils.getUserAgent(httpRequest);

    boolean isError = false;
    String errorMessage = null;
    SearchExecuteResponse response = null;
    long responseTimeMs = 0;

    try {
      long startTime = System.currentTimeMillis();

      response = searchService.searchProductsSimulation(request);
      responseTimeMs = System.currentTimeMillis() - startTime;

      log.info(
          "상품 검색 시뮬레이션 완료 - 환경: {}, 소요시간: {}ms, 결과수: {}",
          request.getEnvironmentType().getDescription(),
          responseTimeMs,
          response.getHits().getData().size());

    } catch (Exception e) {
      isError = true;
      errorMessage = e.getMessage();
      responseTimeMs =
          System.currentTimeMillis()
              - (requestTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

      log.error(
          "상품 검색 시뮬레이션 실패 - 환경: {}, 검색어: {}, 에러: {}",
          request.getEnvironmentType().getDescription(),
          request.getQuery(),
          e.getMessage(),
          e);
      throw e;

    } finally {
      try {
        searchLogService.collectSearchLogSimulation(
            request,
            requestTime,
            clientIp,
            userAgent,
            responseTimeMs,
            response,
            isError,
            errorMessage);
      } catch (Exception logError) {
        log.warn("검색 시뮬레이션 로그 수집 실패: {}", logError.getMessage(), logError);
      }
    }

    return ResponseEntity.ok(response);
  }

  @Operation(summary = "자동완성 검색 시뮬레이션", description = "개발/운영 환경을 선택하여 키워드를 기반으로 자동완성 결과를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "500", description = "자동완성 검색 실패")
  })
  @GetMapping("/autocomplete/simulation")
  public ResponseEntity<AutocompleteResponse> autocompleteSimulation(
      @Parameter(description = "검색 키워드", required = true)
          @RequestParam
          @NotBlank(message = "검색 키워드는 필수입니다")
          @Size(min = 1, max = 100, message = "검색 키워드는 1자 이상 100자 이하여야 합니다")
          String keyword,
      @Parameter(description = "환경 타입 (DEV: 개발, PROD: 운영)", required = true) @RequestParam
          IndexEnvironment.EnvironmentType environmentType) {

    log.info("자동완성 검색 시뮬레이션 요청 - 환경: {}, 키워드: {}", environmentType.getDescription(), keyword);

    try {
      AutocompleteResponse response =
          searchService.getAutocompleteSuggestionsSimulation(keyword, environmentType);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("자동완성 검색 시뮬레이션 실패 - 환경: {}, 키워드: {}", environmentType.getDescription(), keyword, e);
      throw e;
    }
  }
}
