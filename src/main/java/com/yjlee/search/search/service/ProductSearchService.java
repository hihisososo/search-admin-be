package com.yjlee.search.search.service;

import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.strategy.SearchStrategy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

  private final List<SearchStrategy> searchStrategies;

  public SearchExecuteResponse search(
      String indexName, SearchExecuteRequest request, boolean withExplain) {
    log.debug(
        "상품 검색 - index: {}, query: {}, mode: {}, explain: {}",
        indexName,
        request.getQuery(),
        request.getSearchMode(),
        withExplain);

    SearchStrategy strategy =
        searchStrategies.stream()
            .filter(s -> s.supports(request))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No search strategy found for mode: " + request.getSearchMode()));

    return strategy.search(indexName, request, withExplain);
  }
}
