package com.yjlee.search.search.service;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Transaction;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.HttpRequestUtils;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.search.dto.*;
import com.yjlee.search.search.exception.SearchException;
import com.yjlee.search.search.service.typo.TypoCorrectionService;
import com.yjlee.search.searchlog.service.SearchLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

  private final IndexResolver indexResolver;
  private final ProductSearchService productSearchService;
  private final AutocompleteSearchService autocompleteSearchService;
  private final TypoCorrectionService typoCorrectionService;
  private final SearchLogService searchLogService;
  private final HttpRequestUtils httpRequestUtils;

  public AutocompleteResponse getAutocompleteSuggestions(String keyword) {
    String indexName = indexResolver.resolveAutocompleteIndex();
    return autocompleteSearchService.search(indexName, keyword);
  }

  public SearchExecuteResponse searchProducts(SearchExecuteRequest request) {
    String indexName = indexResolver.resolveProductIndex();
    return productSearchService.search(indexName, request, false);
  }

  public SearchExecuteResponse searchProductsSimulation(SearchSimulationRequest request) {
    log.info(
        "상품 검색 시뮬레이션 요청 - 환경: {}, 검색어: {}",
        request.getEnvironmentType().getDescription(),
        request.getQuery());

    String indexName = indexResolver.resolveProductIndexForSimulation(request.getEnvironmentType());
    return productSearchService.search(indexName, request, request.isExplain());
  }

  public AutocompleteResponse getAutocompleteSuggestionsSimulation(
      String keyword, IndexEnvironment.EnvironmentType environmentType) {

    log.info("자동완성 시뮬레이션 요청 - 환경: {}, 키워드: {}", environmentType.getDescription(), keyword);
    String indexName = indexResolver.resolveAutocompleteIndexForSimulation(environmentType);
    return autocompleteSearchService.search(indexName, keyword);
  }

  public void updateTypoCorrectionCacheRealtime(DictionaryEnvironmentType environmentType) {
    typoCorrectionService.updateCacheRealtime(environmentType);
  }

  public String getTypoCorrectionCacheStatus() {
    return typoCorrectionService.getCacheStatus();
  }

  public SearchExecuteResponse executeSearch(SearchParams params, HttpServletRequest httpRequest) {
    SearchExecuteRequest request = buildSearchRequest(params);
    return executeWithLogging(request, httpRequest, () -> searchProducts(request));
  }

  public SearchExecuteResponse executeSearchSimulation(
      SearchSimulationParams params, HttpServletRequest httpRequest) {
    SearchSimulationRequest request = buildSearchSimulationRequest(params);
    return searchProductsSimulation(request);
  }

  private SearchExecuteResponse executeWithLogging(
      SearchExecuteRequest request, HttpServletRequest httpRequest, SearchExecutor executor) {

    LocalDateTime requestTime = LocalDateTime.now(java.time.ZoneOffset.UTC);
    String clientIp = httpRequestUtils.getClientIp(httpRequest);
    String userAgent = httpRequestUtils.getUserAgent(httpRequest);
    String sessionId =
        request.getSearchSessionId() != null
            ? request.getSearchSessionId()
            : UUID.randomUUID().toString();
    long startTime = System.currentTimeMillis();

    Transaction txn = ElasticApm.currentTransaction();
    if (txn != null) {
      txn.setLabel("search.query", request.getQuery());
    }

    try {
      SearchExecuteResponse response = executor.execute();
      long responseTime = System.currentTimeMillis() - startTime;

      collectSearchLog(
          request,
          requestTime,
          clientIp,
          userAgent,
          responseTime,
          response,
          false,
          null,
          sessionId);
      return response;

    } catch (Exception e) {
      long responseTime = System.currentTimeMillis() - startTime;
      collectSearchLog(
          request,
          requestTime,
          clientIp,
          userAgent,
          responseTime,
          null,
          true,
          e.getMessage(),
          sessionId);
      throw new SearchException("상품 검색 실패", e);
    }
  }

  private void collectSearchLog(
      SearchExecuteRequest request,
      LocalDateTime requestTime,
      String clientIp,
      String userAgent,
      long responseTime,
      SearchExecuteResponse response,
      boolean isError,
      String errorMessage,
      String sessionId) {
    try {
      searchLogService.collectSearchLog(
          request,
          requestTime,
          clientIp,
          userAgent,
          responseTime,
          response,
          isError,
          errorMessage,
          sessionId);
    } catch (Exception e) {
      log.warn("Failed to collect search log: {}", e.getMessage());
    }
  }

  private SearchExecuteRequest buildSearchRequest(SearchParams params) {
    SearchExecuteRequest request = new SearchExecuteRequest();
    request.setQuery(params.getQuery());
    request.setPage(params.getPage() != null ? params.getPage() : 0);
    request.setSize(params.getSize() != null ? params.getSize() : 20);
    request.setSearchSessionId(params.getSearchSessionId());

    if (params.getSortField() != null || params.getSortOrder() != null) {
      ProductSortDto sort = new ProductSortDto();
      sort.setField(params.getSortField());
      sort.setOrder(params.getSortOrder());
      request.setSort(sort);
    }

    if (params.getBrand() != null
        || params.getCategory() != null
        || params.getPriceFrom() != null
        || params.getPriceTo() != null) {
      ProductFiltersDto filters = new ProductFiltersDto();
      filters.setBrand(params.getBrand());
      filters.setCategory(params.getCategory());

      if (params.getPriceFrom() != null || params.getPriceTo() != null) {
        PriceRangeDto priceRange = new PriceRangeDto();
        priceRange.setFrom(params.getPriceFrom());
        priceRange.setTo(params.getPriceTo());
        filters.setPriceRange(priceRange);
      }
      request.setFilters(filters);
    }
    return request;
  }

  private SearchSimulationRequest buildSearchSimulationRequest(SearchSimulationParams params) {
    SearchSimulationRequest request = new SearchSimulationRequest();
    request.setEnvironmentType(params.getEnvironmentType());
    request.setExplain(params.isExplain());
    request.setQuery(params.getQuery());
    request.setPage(params.getPage() != null ? params.getPage() : 0);
    request.setSize(params.getSize() != null ? params.getSize() : 20);

    if (params.getSortField() != null || params.getSortOrder() != null) {
      ProductSortDto sort = new ProductSortDto();
      sort.setField(params.getSortField());
      sort.setOrder(params.getSortOrder());
      request.setSort(sort);
    }

    if (params.getBrand() != null
        || params.getCategory() != null
        || params.getPriceFrom() != null
        || params.getPriceTo() != null) {
      ProductFiltersDto filters = new ProductFiltersDto();
      filters.setBrand(params.getBrand());
      filters.setCategory(params.getCategory());

      if (params.getPriceFrom() != null || params.getPriceTo() != null) {
        PriceRangeDto priceRange = new PriceRangeDto();
        priceRange.setFrom(params.getPriceFrom());
        priceRange.setTo(params.getPriceTo());
        filters.setPriceRange(priceRange);
      }
      request.setFilters(filters);
    }
    return request;
  }

  @FunctionalInterface
  private interface SearchExecutor {
    SearchExecuteResponse execute() throws Exception;
  }
}
