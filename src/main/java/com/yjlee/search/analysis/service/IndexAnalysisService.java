package com.yjlee.search.analysis.service;

import com.yjlee.search.analysis.dto.AnalysisRequest;
import com.yjlee.search.analysis.dto.IndexAnalysisResponse;
import com.yjlee.search.analysis.model.TokenGraph;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class IndexAnalysisService extends BaseAnalysisService {

  public IndexAnalysisService(
      IndexEnvironmentRepository indexEnvironmentRepository,
      TempIndexService tempIndexService,
      TokenAnalysisService tokenAnalysisService) {
    super(indexEnvironmentRepository, tempIndexService, tokenAnalysisService);
  }

  public IndexAnalysisResponse analyzeForIndexing(AnalysisRequest request) {
    String query = request.getQuery();
    EnvironmentType environment = request.getEnvironment();

    log.debug("색인 분석 시작 - 쿼리: {}, 환경: {}", query, environment);

    try {
      String preprocessedQuery = preprocess(query);
      TokenGraph tokenGraph = analyzeTokenGraph(query, environment, "nori_index_analyzer");

      // 일반 토큰 추출 (type != "additional")
      List<String> tokens =
          tokenGraph.getEdges().stream()
              .filter(edge -> !"additional".equals(edge.getType()))
              .map(edge -> edge.getToken())
              .distinct()
              .collect(Collectors.toList());

      // 추가 색인어 추출 (type = "additional")
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
      log.error("색인 분석 중 오류 발생 - 쿼리: {}, 환경: {}", query, environment, e);
      throw new RuntimeException("색인 분석 실패: " + e.getMessage(), e);
    }
  }
}
