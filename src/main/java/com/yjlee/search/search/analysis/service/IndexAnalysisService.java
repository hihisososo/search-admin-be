package com.yjlee.search.search.analysis.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.search.analysis.dto.IndexAnalysisRequest;
import com.yjlee.search.search.analysis.dto.IndexAnalysisResponse;
import com.yjlee.search.search.analysis.model.TokenGraph;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexAnalysisService {

  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final TempIndexService tempIndexService;
  private final TokenAnalysisService tokenAnalysisService;

  public IndexAnalysisResponse analyzeForIndexing(IndexAnalysisRequest request) {
    String query = request.getQuery();
    DictionaryEnvironmentType environment = request.getEnvironment();

    log.debug("색인 분석 시작 - 쿼리: {}, 환경: {}", query, environment);

    try {
      // 전처리 수행
      String preprocessedQuery = TextPreprocessor.preprocess(query);

      // Token Graph 분석 (색인 analyzer 사용)
      TokenGraph tokenGraph =
          tokenAnalysisService.analyzeWithTokenGraph(query, environment, "nori_index_analyzer");

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
          .environment(environment.name())
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

  private String getIndexNameForAnalysis(DictionaryEnvironmentType environment) throws IOException {
    if (environment == DictionaryEnvironmentType.CURRENT) {
      // CURRENT 환경은 임시 인덱스 사용
      if (!tempIndexService.isTempIndexExists()) {
        log.info("임시 인덱스가 없어 새로 생성합니다");
        tempIndexService.refreshTempIndex();
      }
      return tempIndexService.getTempIndexName();
    }

    // DEV/PROD 환경은 기존 인덱스 사용
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
