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
    log.info(
        "Product search - index: {}, query: {}, mode: {}, explain: {}",
        indexName,
        request.getQuery(),
        request.getSearchMode(),
        withExplain);

    // 적절한 전략 선택
    SearchStrategy strategy =
        searchStrategies.stream()
            .filter(s -> s.supports(request))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No search strategy found for mode: " + request.getSearchMode()));

    // 전략 실행
    return strategy.search(indexName, request, withExplain);
  }
}
