package com.yjlee.search.analysis.service;

import static com.yjlee.search.common.constants.ESFields.*;

import com.yjlee.search.analysis.domain.TokenGraph;
import com.yjlee.search.analysis.domain.TokenGraphParser;
import com.yjlee.search.analysis.dto.AnalysisRequest;
import com.yjlee.search.analysis.dto.IndexAnalysisResponse;
import com.yjlee.search.analysis.dto.QueryAnalysisResponse;
import com.yjlee.search.analysis.enums.AnalysisType;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
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
public class AnalysisService {

  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final TempIndexService tempIndexService;
  private final RestClient restClient;
  private final TokenGraphParser tokenGraphParser;

  public QueryAnalysisResponse analyzeQuery(AnalysisRequest request) {
    String query = request.getQuery();
    EnvironmentType environment = request.getEnvironment();

    log.debug("쿼리 분석 요청 - 쿼리: {}, 환경: {}", query, environment);

    try {
      String preprocessedQuery = TextPreprocessor.preprocess(query);
      TokenGraph tokenGraph = analyze(query, environment, AnalysisType.SEARCH);

      List<String> tokens =
          tokenGraph.getEdges().stream()
              .filter(edge -> !edge.isSynonym())
              .map(edge -> edge.getToken())
              .collect(Collectors.toList());

      String mermaidGraph = tokenGraph.generateMermaidDiagram();

      return QueryAnalysisResponse.builder()
          .originalQuery(query)
          .preprocessedQuery(preprocessedQuery)
          .tokens(tokens)
          .mermaidGraph(mermaidGraph)
          .build();

    } catch (Exception e) {
      throw new RuntimeException("쿼리 분석 실패: " + e.getMessage(), e);
    }
  }

  public IndexAnalysisResponse analyzeForIndexing(AnalysisRequest request) {
    String query = request.getQuery();
    EnvironmentType environment = request.getEnvironment();

    log.debug("색인 분석 시작 - 쿼리: {}, 환경: {}", query, environment);

    try {
      String preprocessedQuery = TextPreprocessor.preprocess(query);
      TokenGraph tokenGraph = analyze(query, environment, AnalysisType.INDEX);

      List<String> tokens =
          tokenGraph.getEdges().stream()
              .filter(edge -> !"additional".equals(edge.getType()))
              .map(edge -> edge.getToken())
              .distinct()
              .collect(Collectors.toList());

      List<String> additionalTokens =
          tokenGraph.getEdges().stream()
              .filter(edge -> "additional".equals(edge.getType()))
              .map(edge -> edge.getToken())
              .distinct()
              .collect(Collectors.toList());

      return IndexAnalysisResponse.builder()
          .originalQuery(query)
          .preprocessedQuery(preprocessedQuery)
          .tokens(tokens)
          .additionalTokens(additionalTokens)
          .build();

    } catch (Exception e) {
      throw new RuntimeException("색인 분석 실패: " + e.getMessage(), e);
    }
  }

  public TokenGraph analyze(String text, EnvironmentType environment, AnalysisType analysisType)
      throws IOException {

    String indexName = getIndexName(environment);
    Response response = executeAnalyzeRequest(indexName, text, analysisType.getAnalyzer());
    String jsonResponse = EntityUtils.toString(response.getEntity());

    return tokenGraphParser.parse(jsonResponse, text, analysisType.getTargetFilter());
  }

  private Response executeAnalyzeRequest(String indexName, String text, String analyzer)
      throws IOException {
    Request request = new Request("GET", "/" + indexName + ANALYZE_ENDPOINT);
    request.setJsonEntity(
        String.format(
            "{\"%s\":\"%s\",\"%s\":\"%s\",\"%s\":true}",
            ANALYZER, analyzer, TEXT, text.replace("\"", "\\\""), EXPLAIN));
    return restClient.performRequest(request);
  }

  private String getIndexName(EnvironmentType environment) throws IOException {
    if (environment == EnvironmentType.CURRENT) {
      if (!tempIndexService.isTempIndexExists()) {
        throw new RuntimeException("임시 인덱스가 존재하지 않습니다. 먼저 임시 인덱스를 생성해주세요.");
      }
      return tempIndexService.getTempIndexName();
    }

    return indexEnvironmentRepository
        .findByEnvironmentType(
            environment == EnvironmentType.PROD ? EnvironmentType.PROD : EnvironmentType.DEV)
        .orElseThrow(
            () -> new RuntimeException(environment.getDescription() + " 환경의 인덱스를 찾을 수 없습니다."))
        .getIndexName();
  }
}
