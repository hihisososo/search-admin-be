package com.yjlee.search.search.service;

import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.search.dto.*;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchRequestBuilderService {

  public SearchExecuteRequest buildSearchRequest(
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

  public SearchSimulationRequest buildSearchSimulationRequest(
      IndexEnvironment.EnvironmentType environmentType,
      String query,
      Integer page,
      Integer size,
      boolean explain,
      String sortField,
      String sortOrder,
      List<String> brand,
      List<String> category,
      Long priceFrom,
      Long priceTo) {

    SearchSimulationRequest request = new SearchSimulationRequest();
    request.setEnvironmentType(environmentType);
    request.setExplain(explain);
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
