package com.yjlee.search.analysis.service;

import com.yjlee.search.analysis.dto.AnalysisRequest;
import com.yjlee.search.analysis.dto.QueryAnalysisResponse;
import com.yjlee.search.analysis.model.TokenGraph;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueryAnalysisService extends BaseAnalysisService {

  public QueryAnalysisService(
      IndexEnvironmentRepository indexEnvironmentRepository,
      TempIndexService tempIndexService,
      TokenAnalysisService tokenAnalysisService) {
    super(indexEnvironmentRepository, tempIndexService, tokenAnalysisService);
  }

  public QueryAnalysisResponse analyzeQuery(AnalysisRequest request) {
    String query = request.getQuery();
    EnvironmentType environment = request.getEnvironment();

    log.info("쿼리 분석 요청 - 쿼리: {}, 환경: {}", query, environment);

    try {
      String preprocessedQuery = preprocess(query);
      TokenGraph tokenGraph = analyzeTokenGraph(query, environment);

      // 토큰 리스트 추출 (동의어 제외, 원본 토큰만)
      List<String> tokens =
          tokenGraph.getEdges().stream()
              .filter(edge -> !edge.isSynonym())
              .map(edge -> edge.getToken())
              .collect(Collectors.toList());

      // Mermaid 다이어그램 생성
      String mermaidGraph = tokenGraph.generateMermaidDiagram();

      return QueryAnalysisResponse.builder()
          .originalQuery(query)
          .preprocessedQuery(preprocessedQuery)
          .tokens(tokens)
          .mermaidGraph(mermaidGraph)
          .build();

    } catch (Exception e) {
      log.error("쿼리 분석 중 오류 발생 - 쿼리: {}, 환경: {}", query, environment, e);
      throw new RuntimeException("쿼리 분석 실패: " + e.getMessage(), e);
    }
  }
}
