package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.search.dto.AutocompleteResponse;
import com.yjlee.search.search.service.builder.QueryResponseBuilder;
import com.yjlee.search.search.service.builder.SearchRequestBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutocompleteSearchService {

  private final SearchRequestBuilder searchRequestBuilder;
  private final QueryResponseBuilder responseBuilder;
  private final SearchQueryExecutor queryExecutor;

  public AutocompleteResponse search(String indexName, String keyword) {
    log.info("Autocomplete search - index: {}, keyword: {}", indexName, keyword);

    long startTime = System.currentTimeMillis();

    SearchRequest searchRequest =
        searchRequestBuilder.buildAutocompleteSearchRequest(indexName, keyword);
    SearchResponse<JsonNode> response = queryExecutor.execute(searchRequest);
    long took = System.currentTimeMillis() - startTime;

    return responseBuilder.buildAutocompleteResponse(response, took, keyword);
  }
}
