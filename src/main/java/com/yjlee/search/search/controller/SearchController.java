package com.yjlee.search.search.controller;

import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchRequest;
import com.yjlee.search.search.service.SearchService;
import com.yjlee.search.searchlog.model.SearchLogDocument;
import com.yjlee.search.searchlog.service.SearchLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
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

  @Operation(summary = "검색 실행")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "400", description = "잘못된 요청"),
    @ApiResponse(responseCode = "500", description = "검색 실행 실패")
  })
  @PostMapping
  public ResponseEntity<SearchExecuteResponse> search(
      @RequestBody @Valid SearchRequest request, HttpServletRequest httpRequest) {

    log.info("검색 실행 요청 - 인덱스: {}, 키워드: {}", request.getIndexName(), request.getSearchKeyword());

    LocalDateTime requestTime = LocalDateTime.now();
    String clientIp = getClientIp(httpRequest);
    String userAgent = getUserAgent(httpRequest);

    boolean isError = false;
    String errorMessage = null;
    SearchExecuteResponse response = null;
    long responseTimeMs = 0;

    try {
      long startTime = System.currentTimeMillis();

      SearchExecuteRequest searchRequest =
          SearchExecuteRequest.builder()
              .indexName(request.getIndexName())
              .queryDsl(request.getQueryDsl())
              .build();

      response = searchService.executeSearch(searchRequest);
      responseTimeMs = System.currentTimeMillis() - startTime;

      log.info("검색 실행 완료 - 소요시간: {}ms", responseTimeMs);

    } catch (Exception e) {
      isError = true;
      errorMessage = e.getMessage();
      responseTimeMs =
          System.currentTimeMillis()
              - (requestTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

      log.error("검색 실행 실패 - 인덱스: {}, 에러: {}", request.getIndexName(), e.getMessage(), e);
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
      SearchRequest request,
      LocalDateTime timestamp,
      String clientIp,
      String userAgent,
      long responseTimeMs,
      SearchExecuteResponse response,
      boolean isError,
      String errorMessage) {

    String keyword =
        request.getSearchKeyword() != null && !request.getSearchKeyword().trim().isEmpty()
            ? request.getSearchKeyword().trim()
            : "unknown";

    Long resultCount = extractResultCount(response);

    SearchLogDocument searchLog =
        SearchLogDocument.builder()
            .timestamp(timestamp)
            .searchKeyword(keyword)
            .indexName(request.getIndexName())
            .responseTimeMs(responseTimeMs)
            .resultCount(resultCount)
            .queryDsl(request.getQueryDsl())
            .clientIp(clientIp)
            .userAgent(userAgent)
            .isError(isError)
            .errorMessage(errorMessage)
            .build();

    searchAnalyticsService.saveSearchLog(searchLog);

    log.debug("검색 로그 수집 완료 - 키워드: {}, 결과수: {}", keyword, resultCount);
  }

  private Long extractResultCount(SearchExecuteResponse response) {
    if (response == null || response.getSearchResult() == null) {
      return 0L;
    }

    try {
      if (response.getSearchResult() instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getSearchResult();

        Object hits = result.get("hits");
        if (hits instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> hitsMap = (Map<String, Object>) hits;

          Object total = hitsMap.get("total");
          if (total instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> totalMap = (Map<String, Object>) total;

            Object value = totalMap.get("value");
            if (value instanceof Number) {
              return ((Number) value).longValue();
            }
          }
        }
      }
    } catch (Exception e) {
      log.debug("검색 결과 수 추출 실패, 기본값 0 사용: {}", e.getMessage());
    }

    return 0L;
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

  private String getUserAgent(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    return userAgent != null ? userAgent : "unknown";
  }
}
