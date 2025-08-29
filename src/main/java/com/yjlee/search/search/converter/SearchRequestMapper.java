package com.yjlee.search.search.converter;

import com.yjlee.search.search.constants.SearchConstants;
import com.yjlee.search.search.dto.PriceRangeDto;
import com.yjlee.search.search.dto.ProductFiltersDto;
import com.yjlee.search.search.dto.ProductSortDto;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchMode;
import com.yjlee.search.search.dto.SearchParams;
import com.yjlee.search.search.dto.SearchSimulationParams;
import com.yjlee.search.search.dto.SearchSimulationRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SearchRequestMapper {

  public SearchExecuteRequest toSearchExecuteRequest(SearchParams params) {
    SearchExecuteRequest request = new SearchExecuteRequest();
    copyCommonFields(params, request);
    return request;
  }

  private void setSort(SearchExecuteRequest request, SearchParams params) {
    if (hasSort(params)) {
      ProductSortDto sort = new ProductSortDto();
      sort.setField(params.getSortField());
      sort.setOrder(params.getSortOrder());
      request.setSort(sort);
    }
  }

  private void setFilters(SearchExecuteRequest request, SearchParams params) {
    if (hasFilters(params)) {
      ProductFiltersDto filters = new ProductFiltersDto();
      filters.setBrand(params.getBrand());
      filters.setCategory(params.getCategory());

      if (hasPriceFilter(params)) {
        PriceRangeDto priceRange = new PriceRangeDto();
        priceRange.setFrom(params.getPriceFrom());
        priceRange.setTo(params.getPriceTo());
        filters.setPriceRange(priceRange);
      }

      request.setFilters(filters);
    }
  }

  private boolean hasSort(SearchParams params) {
    return params.getSortField() != null || params.getSortOrder() != null;
  }

  private boolean hasFilters(SearchParams params) {
    return params.getBrand() != null || params.getCategory() != null || hasPriceFilter(params);
  }

  private boolean hasPriceFilter(SearchParams params) {
    return params.getPriceFrom() != null || params.getPriceTo() != null;
  }

  public SearchSimulationRequest toSearchSimulationRequest(SearchSimulationParams params) {
    SearchSimulationRequest request = new SearchSimulationRequest();

    // SearchExecuteRequest의 모든 필드 설정
    copyCommonFields(params, request);

    // SearchSimulationRequest 특정 필드 설정
    request.setEnvironmentType(params.getEnvironmentType());
    request.setExplain(params.isExplain());

    return request;
  }

  private void copyCommonFields(SearchParams params, SearchExecuteRequest request) {
    // 기본 검색 파라미터
    request.setQuery(params.getQuery());
    request.setPage(Optional.ofNullable(params.getPage()).orElse(SearchConstants.DEFAULT_PAGE));
    request.setSize(Optional.ofNullable(params.getSize()).orElse(20));
    request.setSearchSessionId(params.getSearchSessionId());

    // 검색 모드 관련
    request.setSearchMode(
        Optional.ofNullable(params.getSearchMode()).orElse(SearchMode.KEYWORD_ONLY));
    request.setRrfK(Optional.ofNullable(params.getRrfK()).orElse(SearchConstants.DEFAULT_RRF_K));
    request.setHybridTopK(
        Optional.ofNullable(params.getHybridTopK()).orElse(SearchConstants.DEFAULT_HYBRID_TOP_K));
    request.setVectorMinScore(params.getVectorMinScore());

    // 정렬 설정
    setSort(request, params);

    // 필터 설정
    setFilters(request, params);
  }
}
