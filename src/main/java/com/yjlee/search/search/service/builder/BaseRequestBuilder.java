package com.yjlee.search.search.service.builder;

import com.yjlee.search.search.dto.PriceRangeDto;
import com.yjlee.search.search.dto.ProductFiltersDto;
import com.yjlee.search.search.dto.ProductSortDto;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import java.util.List;

public abstract class BaseRequestBuilder {

  protected void populateBaseRequest(
      SearchExecuteRequest request,
      String query,
      Integer page,
      Integer size,
      String sortField,
      String sortOrder,
      List<String> brand,
      List<String> category,
      Long priceFrom,
      Long priceTo) {

    request.setQuery(query);
    request.setPage(page);
    request.setSize(size);

    ProductSortDto sort = buildSortDto(sortField, sortOrder);
    if (sort != null) {
      request.setSort(sort);
    }

    ProductFiltersDto filters = buildFiltersDto(brand, category, priceFrom, priceTo);
    if (filters != null) {
      request.setFilters(filters);
    }
  }

  private ProductSortDto buildSortDto(String sortField, String sortOrder) {
    if (sortField == null && sortOrder == null) {
      return null;
    }

    ProductSortDto sort = new ProductSortDto();
    sort.setField(sortField);
    sort.setOrder(sortOrder);
    return sort;
  }

  private ProductFiltersDto buildFiltersDto(
      List<String> brand, List<String> category, Long priceFrom, Long priceTo) {

    if (brand == null && category == null && priceFrom == null && priceTo == null) {
      return null;
    }

    ProductFiltersDto filters = new ProductFiltersDto();
    filters.setBrand(brand);
    filters.setCategory(category);

    if (priceFrom != null || priceTo != null) {
      PriceRangeDto priceRange = new PriceRangeDto();
      priceRange.setFrom(priceFrom);
      priceRange.setTo(priceTo);
      filters.setPriceRange(priceRange);
    }

    return filters;
  }
}
