package com.yjlee.search.analysis.service;

import com.yjlee.search.analysis.domain.TokenInfo;
import com.yjlee.search.analysis.dto.AnalysisRequest;
import com.yjlee.search.analysis.dto.IndexAnalysisResponse;
import com.yjlee.search.analysis.dto.QueryAnalysisResponse;
import com.yjlee.search.analysis.enums.AnalysisType;
import com.yjlee.search.analysis.exception.AnalysisException;
import com.yjlee.search.analysis.exception.TempIndexNotFoundException;
import com.yjlee.search.analysis.util.MermaidDiagramGenerator;
import com.yjlee.search.analysis.util.TokenParser;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.deployment.service.IndexEnvironmentService;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisService {

  private final IndexEnvironmentService environmentService;
  private final TempIndexService tempIndexService;
  private final ElasticsearchAnalyzer elasticsearchAnalyzer;

  public QueryAnalysisResponse analyzeQuery(AnalysisRequest request) {
    String query = request.getQuery();
    EnvironmentType environment = request.getEnvironment();

    try {
      String preprocessedQuery = TextPreprocessor.preprocess(query);
      List<TokenInfo> tokens = analyze(query, environment, AnalysisType.SEARCH);

      List<String> tokenStrings =
          tokens.stream()
              .filter(token -> !token.isSynonym())
              .map(TokenInfo::getToken)
              .collect(Collectors.toList());
      String mermaidGraph = MermaidDiagramGenerator.generate(tokens);

      return QueryAnalysisResponse.builder()
          .originalQuery(query)
          .preprocessedQuery(preprocessedQuery)
          .tokens(tokenStrings)
          .mermaidGraph(mermaidGraph)
          .build();

    } catch (IOException e) {
      throw new AnalysisException("쿼리 분석 실패: " + e.getMessage(), e);
    }
  }

  public IndexAnalysisResponse analyzeForIndexing(AnalysisRequest request) {
    String query = request.getQuery();
    EnvironmentType environment = request.getEnvironment();

    try {
      String preprocessedQuery = TextPreprocessor.preprocess(query);
      List<TokenInfo> tokens = analyze(query, environment, AnalysisType.INDEX);

      List<String> tokenStrings =
          tokens.stream()
              .filter(token -> !token.isAdditional())
              .map(TokenInfo::getToken)
              .distinct()
              .collect(Collectors.toList());
      List<String> additionalTokens =
          tokens.stream()
              .filter(TokenInfo::isAdditional)
              .map(TokenInfo::getToken)
              .distinct()
              .collect(Collectors.toList());

      return IndexAnalysisResponse.builder()
          .originalQuery(query)
          .preprocessedQuery(preprocessedQuery)
          .tokens(tokenStrings)
          .additionalTokens(additionalTokens)
          .build();

    } catch (IOException e) {
      throw new AnalysisException("색인 분석 실패: " + e.getMessage(), e);
    }
  }

  public List<TokenInfo> analyze(
      String text, EnvironmentType environment, AnalysisType analysisType) throws IOException {

    String indexName = getIndexName(environment);
    String jsonResponse =
        elasticsearchAnalyzer.analyze(indexName, text, analysisType.getAnalyzer());

    return TokenParser.parse(jsonResponse, analysisType.getTargetFilter());
  }

  private String getIndexName(EnvironmentType environment) throws IOException {
    if (environment == EnvironmentType.CURRENT) {
      if (!tempIndexService.isTempIndexExists()) {
        throw new TempIndexNotFoundException();
      }
      return tempIndexService.getTempIndexName();
    }

    var env = environmentService.getEnvironmentOrNull(environment);
    if (env == null) {
      throw new AnalysisException(environment.getDescription() + " 환경의 인덱스를 찾을 수 없습니다.");
    }
    return env.getIndexName();
  }
}
