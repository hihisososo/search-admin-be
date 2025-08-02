package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.service.builder.QueryBuilder;
import com.yjlee.search.search.service.builder.QueryResponseBuilder;
import com.yjlee.search.search.service.builder.SearchRequestBuilder;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

  private final QueryBuilder queryBuilder;
  private final SearchRequestBuilder searchRequestBuilder;
  private final QueryResponseBuilder responseBuilder;
  private final SearchQueryExecutor queryExecutor;

  public SearchExecuteResponse search(
      String indexName, SearchExecuteRequest request, boolean withExplain) {
    log.info(
        "Product search - index: {}, query: {}, explain: {}",
        indexName,
        request.getQuery(),
        withExplain);

    long startTime = System.currentTimeMillis();

    BoolQuery boolQuery = queryBuilder.buildBoolQuery(request);
    Map<String, Aggregation> aggregations = searchRequestBuilder.buildAggregations();
    SearchRequest searchRequest =
        searchRequestBuilder.buildProductSearchRequest(
            indexName, request, boolQuery, aggregations, withExplain);

    SearchResponse<JsonNode> response = queryExecutor.execute(searchRequest);
    long took = System.currentTimeMillis() - startTime;

    return responseBuilder.buildSearchResponse(request, response, took, withExplain);
  }
}
