package com.yjlee.search.search.analysis.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.search.analysis.dto.QueryAnalysisRequest;
import com.yjlee.search.search.analysis.dto.QueryAnalysisResponse;
import com.yjlee.search.search.analysis.model.TokenGraph;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryAnalysisService {

  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final TempIndexService tempIndexService;
  private final TokenAnalysisService tokenAnalysisService;

  public QueryAnalysisResponse analyzeQuery(QueryAnalysisRequest request) {
    String query = request.getQuery();
    DictionaryEnvironmentType environment = request.getEnvironment();

    log.debug("쿼리 분석 시작 - 쿼리: {}, 환경: {}", query, environment);

    try {
      // Token Graph 분석
      TokenGraph tokenGraph = tokenAnalysisService.analyzeWithTokenGraph(query, environment);

      // 토큰 리스트 추출 (동의어 제외, 원본 토큰만)
      List<String> tokens =
          tokenGraph.getEdges().stream()
              .filter(edge -> !edge.isSynonym())
              .map(edge -> edge.getToken())
              .collect(Collectors.toList());

      // 동의어 확장 정보 추출
      Map<String, List<String>> synonymExpansions = tokenGraph.extractSynonymExpansions();

      // Mermaid 다이어그램 생성
      String mermaidGraph = tokenGraph.generateMermaidDiagram();

      return QueryAnalysisResponse.builder()
          .environment(environment.name())
          .originalQuery(query)
          .tokens(tokens)
          .synonymExpansions(synonymExpansions)
          .mermaidGraph(mermaidGraph)
          .build();

    } catch (Exception e) {
      log.error("쿼리 분석 중 오류 발생 - 쿼리: {}, 환경: {}", query, environment, e);
      throw new RuntimeException("쿼리 분석 실패: " + e.getMessage(), e);
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
