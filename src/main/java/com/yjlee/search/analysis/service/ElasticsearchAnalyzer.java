package com.yjlee.search.analysis.service;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchAnalyzer {
  private static final String ANALYZER = "analyzer";
  private static final String TEXT = "text";
  private static final String EXPLAIN = "explain";
  private static final String ANALYZE_ENDPOINT = "/_analyze";

  private final RestClient restClient;

  public String analyze(String indexName, String text, String analyzer) throws IOException {
    Request request = createAnalyzeRequest(indexName, text, analyzer);
    Response response = restClient.performRequest(request);
    String jsonResponse = EntityUtils.toString(response.getEntity());
    return jsonResponse;
  }

  private Request createAnalyzeRequest(String indexName, String text, String analyzer) {
    Request request = new Request("GET", "/" + indexName + ANALYZE_ENDPOINT);
    String jsonEntity = buildAnalyzeJson(analyzer, text);
    request.setJsonEntity(jsonEntity);
    return request;
  }

  private String buildAnalyzeJson(String analyzer, String text) {
    return String.format(
        "{\"%s\":\"%s\",\"%s\":\"%s\",\"%s\":true}",
        ANALYZER, analyzer, TEXT, escapeJson(text), EXPLAIN);
  }

  private String escapeJson(String text) {
    return text.replace("\"", "\\\"");
  }
}
