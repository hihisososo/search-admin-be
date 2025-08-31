package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.search.exception.SearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchQueryExecutor {

  private final ElasticsearchClient esClient;

  public SearchResponse<JsonNode> execute(SearchRequest request) {
    try {
      log.debug("Executing search request: {}", request);
      return esClient.search(request, JsonNode.class);
    } catch (Exception e) {
      log.error("Search execution failed", e);
      throw new SearchException("Search execution failed: " + e.getMessage(), e);
    }
  }

  public GetResponse<JsonNode> getDocument(String indexName, String documentId) {
    try {
      log.debug("Getting document from index: {}, documentId: {}", indexName, documentId);
      GetRequest request = GetRequest.of(r -> r.index(indexName).id(documentId));
      return esClient.get(request, JsonNode.class);
    } catch (Exception e) {
      log.error(
          "Document retrieval failed for index: {}, documentId: {}", indexName, documentId, e);
      throw new SearchException("Document retrieval failed: " + e.getMessage(), e);
    }
  }
}
