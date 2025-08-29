package com.yjlee.search.search.strategy;

import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchMode;
import com.yjlee.search.search.service.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HybridSearchStrategy implements SearchStrategy {

  private final HybridSearchService hybridSearchService;

  @Override
  public SearchExecuteResponse search(String indexName, SearchExecuteRequest request, boolean withExplain) {
    log.info("Executing hybrid RRF search for query: {}", request.getQuery());
    return hybridSearchService.hybridSearch(indexName, request, withExplain);
  }

  @Override
  public boolean supports(SearchExecuteRequest request) {
    return request.getSearchMode() == SearchMode.HYBRID_RRF;
  }
}