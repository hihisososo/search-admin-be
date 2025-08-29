package com.yjlee.search.search.strategy;

import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;

public interface SearchStrategy {
  
  SearchExecuteResponse search(String indexName, SearchExecuteRequest request, boolean withExplain);
  
  boolean supports(SearchExecuteRequest request);
}