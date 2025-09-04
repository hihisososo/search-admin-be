package com.yjlee.search.search.analysis.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.search.analysis.model.TokenGraph;
import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenAnalysisService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final TempIndexService tempIndexService;
  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TokenGraph analyzeWithTokenGraph(String text, DictionaryEnvironmentType environment)
      throws IOException {
    return analyzeWithTokenGraph(text, environment, "nori_search_analyzer");
  }

  public TokenGraph analyzeWithTokenGraph(
      String text, DictionaryEnvironmentType environment, String analyzer) throws IOException {

    String indexName = getIndexNameForAnalysis(environment);

    Request request = new Request("GET", "/" + indexName + "/_analyze");
    String jsonBody =
        String.format(
            "{\"analyzer\":\"%s\",\"text\":\"%s\",\"explain\":true}",
            analyzer, text.replace("\"", "\\\""));
    request.setJsonEntity(jsonBody);

    Response response = restClient.performRequest(request);
    String responseBody = EntityUtils.toString(response.getEntity());
    JsonNode root = objectMapper.readTree(responseBody);

    TokenGraph tokenGraph = TokenGraph.builder().originalQuery(text).build();

    JsonNode detail = root.get("detail");
    if (detail != null) {
      processTokenStream(detail, tokenGraph, analyzer);
    }

    tokenGraph.generatePaths();

    log.debug(
        "Token graph analysis complete - {} nodes, {} edges, {} paths",
        tokenGraph.getPositionNodes().size(),
        tokenGraph.getEdges().size(),
        tokenGraph.getPaths().size());

    return tokenGraph;
  }

  private void processTokenStream(JsonNode detail, TokenGraph tokenGraph, String analyzer) {
    JsonNode tokenFilters = detail.get("tokenfilters");
    if (tokenFilters == null) {
      return;
    }

    // analyzer 타입에 따라 적절한 필터 선택
    String targetFilter;
    if ("nori_index_analyzer".equals(analyzer)) {
      // 색인용 분석기는 stopword_filter에서 토큰 가져오기
      targetFilter = "stopword_filter";
    } else {
      // 검색용 분석기는 search_synonym_filter에서 토큰 가져오기
      targetFilter = "search_synonym_filter";
    }

    for (JsonNode filter : tokenFilters) {
      String filterName = filter.get("name").asText();

      if (targetFilter.equals(filterName)) {
        JsonNode tokens = filter.get("tokens");
        if (tokens != null) {
          processTokens(tokens, tokenGraph);
        }
        break;
      }
    }
  }

  private void processTokens(JsonNode tokens, TokenGraph tokenGraph) {
    for (JsonNode token : tokens) {
      int position = token.get("position").asInt();
      int positionLength = token.has("positionLength") ? token.get("positionLength").asInt() : 1;
      String type = token.has("type") ? token.get("type").asText() : "word";
      int startOffset = token.get("start_offset").asInt();
      int endOffset = token.get("end_offset").asInt();
      String tokenText = token.get("token").asText();

      JsonNode attributes = token.get("attributes");
      if (attributes != null && attributes.has("positionLength")) {
        positionLength = attributes.get("positionLength").asInt();
      }

      TokenGraph.TokenInfo tokenInfo =
          TokenGraph.TokenInfo.builder()
              .token(tokenText)
              .type(type)
              .position(position)
              .positionLength(positionLength)
              .startOffset(startOffset)
              .endOffset(endOffset)
              .build();

      tokenGraph.addToken(tokenInfo);
    }
  }

  private String getIndexNameForAnalysis(DictionaryEnvironmentType environment) throws IOException {
    if (environment == DictionaryEnvironmentType.CURRENT) {
      if (!tempIndexService.isTempIndexExists()) {
        log.info("임시 인덱스가 없어 새로 생성합니다");
        tempIndexService.refreshTempIndex();
      }
      return tempIndexService.getTempIndexName();
    }

    IndexEnvironment.EnvironmentType envType =
        environment == DictionaryEnvironmentType.PROD
            ? IndexEnvironment.EnvironmentType.PROD
            : IndexEnvironment.EnvironmentType.DEV;

    return indexEnvironmentRepository
        .findByEnvironmentType(envType)
        .map(IndexEnvironment::getIndexName)
        .orElseThrow(
            () -> new RuntimeException(environment.getDescription() + " 환경의 인덱스를 찾을 수 없습니다."));
  }
}
