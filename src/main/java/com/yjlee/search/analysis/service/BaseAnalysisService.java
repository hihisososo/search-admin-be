package com.yjlee.search.analysis.service;

import com.yjlee.search.analysis.model.TokenGraph;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseAnalysisService {

  protected final IndexEnvironmentRepository indexEnvironmentRepository;
  protected final TempIndexService tempIndexService;
  protected final TokenAnalysisService tokenAnalysisService;

  protected String preprocess(String query) {
    return TextPreprocessor.preprocess(query);
  }

  protected TokenGraph analyzeTokenGraph(String query, EnvironmentType environment) {
    try {
      return tokenAnalysisService.analyzeWithTokenGraph(query, environment);
    } catch (Exception e) {
      throw new RuntimeException("토큰 그래프 분석 실패: " + e.getMessage(), e);
    }
  }

  protected TokenGraph analyzeTokenGraph(
      String query, EnvironmentType environment, String analyzer) {
    try {
      return tokenAnalysisService.analyzeWithTokenGraph(query, environment, analyzer);
    } catch (Exception e) {
      throw new RuntimeException("토큰 그래프 분석 실패: " + e.getMessage(), e);
    }
  }
}
