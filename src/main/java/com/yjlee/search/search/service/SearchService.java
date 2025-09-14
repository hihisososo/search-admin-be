package com.yjlee.search.search.service;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Transaction;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.util.HttpRequestUtils;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.index.provider.IndexNameProvider;
import com.yjlee.search.search.converter.SearchRequestMapper;
import com.yjlee.search.search.dto.*;
import com.yjlee.search.search.service.typo.TypoCorrectionService;
import com.yjlee.search.searchlog.service.SearchLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final IndexNameProvider indexNameProvider;
  private final ProductSearchService productSearchService;
  private final AutocompleteSearchService autocompleteSearchService;
  private final TypoCorrectionService typoCorrectionService;
  private final SearchLogService searchLogService;
  private final SearchRequestMapper searchRequestMapper;
  private final SearchQueryExecutor searchQueryExecutor;

  public AutocompleteResponse getAutocompleteSuggestions(String keyword) {
    String indexName = indexNameProvider.getAutocompleteSearchAlias();
    return autocompleteSearchService.search(indexName, keyword);
  }

  public SearchExecuteResponse searchProducts(SearchExecuteRequest request) {
    String indexName = indexNameProvider.getProductsSearchAlias();

    // 검색 실행
    long startTime = System.currentTimeMillis();
    SearchExecuteResponse response = productSearchService.search(indexName, request, false);
    long responseTime = System.currentTimeMillis() - startTime;

    // 로깅
    logSearch(request, response, responseTime);

    return response;
  }

  public SearchExecuteResponse searchProductsSimulation(SearchSimulationRequest request) {
    log.info(
        "상품 검색 시뮬레이션 요청 - 환경: {}, 검색어: {}",
        request.getEnvironmentType().getDescription(),
        request.getQuery());

    IndexEnvironment environment =
        indexEnvironmentRepository
            .findByEnvironmentType(request.getEnvironmentType())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        request.getEnvironmentType().getDescription() + " 환경을 찾을 수 없습니다."));
    String indexName = environment.getIndexName();

    return productSearchService.search(
        indexName, (SearchExecuteRequest) request, request.isExplain());
  }

  public AutocompleteResponse getAutocompleteSuggestionsSimulation(
      String keyword, EnvironmentType environmentType) {

    log.info("자동완성 시뮬레이션 요청 - 환경: {}, 키워드: {}", environmentType.getDescription(), keyword);
    IndexEnvironment environment =
        indexEnvironmentRepository
            .findByEnvironmentType(environmentType)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        environmentType.getDescription() + " 환경을 찾을 수 없습니다."));
    String indexName = environment.getAutocompleteIndexName();
    return autocompleteSearchService.search(indexName, keyword);
  }

  public void updateTypoCorrectionCacheRealtime(EnvironmentType environmentType) {
    typoCorrectionService.updateCacheRealtime(environmentType);
  }

  public String getTypoCorrectionCacheStatus() {
    // Spring Cache로 마이그레이션됨 - 캐시 상태는 CacheManager를 통해 확인
    return "Cache managed by Spring Cache";
  }

  public SearchExecuteResponse executeSearch(SearchParams params, HttpServletRequest httpRequest) {
    SearchExecuteRequest request = searchRequestMapper.toSearchExecuteRequest(params);

    // HTTP 요청 정보와 함께 검색 실행
    String indexName = indexNameProvider.getProductsSearchAlias();

    long startTime = System.currentTimeMillis();
    SearchExecuteResponse response = productSearchService.search(indexName, request, false);
    long responseTime = System.currentTimeMillis() - startTime;

    // HTTP 컨텍스트 정보로 로깅
    logSearchWithHttpContext(request, response, responseTime, httpRequest);

    return response;
  }

  public SearchExecuteResponse executeSearchSimulation(
      SearchSimulationParams params, HttpServletRequest httpRequest) {
    SearchSimulationRequest request = searchRequestMapper.toSearchSimulationRequest(params);
    return searchProductsSimulation(request);
  }

  public JsonNode getDocumentById(String documentId, EnvironmentType environmentType) {
    String indexName;
    if (environmentType != null) {
      IndexEnvironment environment =
          indexEnvironmentRepository
              .findByEnvironmentType(environmentType)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          environmentType.getDescription() + " 환경을 찾을 수 없습니다."));
      indexName = environment.getIndexName();
    } else {
      indexName = indexNameProvider.getProductsSearchAlias();
    }

    co.elastic.clients.elasticsearch.core.GetResponse<com.fasterxml.jackson.databind.JsonNode>
        response = searchQueryExecutor.getDocument(indexName, documentId);

    if (!response.found()) {
      throw new RuntimeException("Document not found: " + documentId);
    }

    return response.source();
  }

  // 일반 검색 로깅 (프로그램 내부용)
  private void logSearch(
      SearchExecuteRequest request, SearchExecuteResponse response, long responseTime) {
    if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
      return;
    }

    String sessionId =
        request.getSearchSessionId() != null
            ? request.getSearchSessionId()
            : UUID.randomUUID().toString();

    try {
      searchLogService.collectSearchLog(
          request,
          LocalDateTime.now(ZoneOffset.UTC),
          "SYSTEM",
          "System/1.0",
          responseTime,
          response,
          false,
          null,
          sessionId);
    } catch (Exception e) {
      log.warn("Failed to collect search log: {}", e.getMessage());
    }
  }

  // HTTP 컨텍스트와 함께 로깅
  private void logSearchWithHttpContext(
      SearchExecuteRequest request,
      SearchExecuteResponse response,
      long responseTime,
      HttpServletRequest httpRequest) {

    if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
      return;
    }

    String sessionId =
        request.getSearchSessionId() != null
            ? request.getSearchSessionId()
            : UUID.randomUUID().toString();
    String clientIp = HttpRequestUtils.getClientIp(httpRequest);
    String userAgent = HttpRequestUtils.getUserAgent(httpRequest);

    Transaction txn = ElasticApm.currentTransaction();
    if (txn != null) {
      txn.setLabel("search.query", Optional.ofNullable(request.getQuery()).orElse(""));
    }

    try {
      searchLogService.collectSearchLog(
          request,
          LocalDateTime.now(ZoneOffset.UTC),
          clientIp,
          userAgent,
          responseTime,
          response,
          false,
          null,
          sessionId);
    } catch (Exception e) {
      log.warn("Failed to collect search log: {}", e.getMessage());
    }
  }
}
