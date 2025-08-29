package com.yjlee.search.search.strategy;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.dto.SearchMode;
import com.yjlee.search.search.service.SearchQueryExecutor;
import com.yjlee.search.search.service.builder.QueryBuilder;
import com.yjlee.search.search.service.builder.QueryResponseBuilder;
import com.yjlee.search.search.service.builder.SearchRequestBuilder;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordSearchStrategy implements SearchStrategy {

  private final QueryBuilder queryBuilder;
  private final SearchRequestBuilder searchRequestBuilder;
  private final QueryResponseBuilder responseBuilder;
  private final SearchQueryExecutor queryExecutor;

  @Override
  public SearchExecuteResponse search(String indexName, SearchExecuteRequest request, boolean withExplain) {
    log.info("Executing keyword-only search for query: {}", request.getQuery());
    
    long startTime = System.currentTimeMillis();
    
    BoolQuery boolQuery = queryBuilder.buildBoolQuery(request);
    Map<String, Aggregation> aggregations = searchRequestBuilder.buildAggregations();
    SearchRequest searchRequest = searchRequestBuilder.buildProductSearchRequest(
        indexName, request, boolQuery, aggregations, withExplain);
    
    SearchResponse<JsonNode> response = queryExecutor.execute(searchRequest);
    long took = System.currentTimeMillis() - startTime;
    
    return responseBuilder.buildSearchResponse(request, response, took, withExplain, searchRequest);
  }

  @Override
  public boolean supports(SearchExecuteRequest request) {
    return request.getSearchMode() == SearchMode.KEYWORD_ONLY;
  }
}