package com.yjlee.search.dictionary.category.service;

import com.yjlee.search.dictionary.category.dto.CategoryListResponse;
import com.yjlee.search.index.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategoryService {

  private final ProductRepository productRepository;

  @Transactional(readOnly = true)
  public CategoryListResponse getCategories() {
    List<String> categories = productRepository.findDistinctCategoryNames();
    categories.sort(String::compareTo);
    return CategoryListResponse.builder()
        .totalCount(categories.size())
        .categories(categories)
        .build();
  }
}
