package com.yjlee.search.search.analysis.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.index.util.ModelExtractor;
import com.yjlee.search.index.util.UnitExtractor;
import com.yjlee.search.search.analysis.dto.QueryAnalysisRequest;
import com.yjlee.search.search.analysis.dto.QueryAnalysisResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryAnalysisService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final TempIndexService tempIndexService;

  public QueryAnalysisResponse analyzeQuery(QueryAnalysisRequest request) {
    String query = request.getQuery();
    DictionaryEnvironmentType environment = request.getEnvironment();

    log.debug("쿼리 분석 시작 - 쿼리: {}, 환경: {}", query, environment);

    try {
      // 1. 인덱스 선택
      String indexName = getIndexNameForAnalysis(environment);

      // 2. Nori 형태소 분석
      QueryAnalysisResponse.NoriAnalysis noriAnalysis = analyzeWithNori(query, indexName);

      // 3. 단위 추출 및 확장
      List<QueryAnalysisResponse.UnitInfo> units = extractAndExpandUnits(query);

      // 4. 모델명 추출 (단위 제외)
      List<String> models = extractModels(query, units);

      return QueryAnalysisResponse.builder()
          .environment(environment.name())
          .originalQuery(query)
          .noriAnalysis(noriAnalysis)
          .units(units)
          .models(models)
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

  private QueryAnalysisResponse.NoriAnalysis analyzeWithNori(String text, String indexName)
      throws IOException {

    // 원본 토큰 추출 (동의어 확장 없이)
    List<QueryAnalysisResponse.TokenInfo> tokens =
        analyzeTokens(text, indexName, "nori_index_analyzer");

    // 토큰별 동의어 매핑
    Map<String, List<String>> synonymExpansions = getTokenSynonymMapping(text, indexName);

    return QueryAnalysisResponse.NoriAnalysis.builder()
        .tokens(tokens)
        .synonymExpansions(synonymExpansions)
        .build();
  }

  private List<QueryAnalysisResponse.TokenInfo> analyzeTokens(
      String text, String indexName, String analyzer) throws IOException {

    AnalyzeRequest analyzeRequest =
        AnalyzeRequest.of(a -> a.index(indexName).analyzer(analyzer).text(text));

    AnalyzeResponse response = elasticsearchClient.indices().analyze(analyzeRequest);

    List<QueryAnalysisResponse.TokenInfo> tokens = new ArrayList<>();
    for (AnalyzeToken token : response.tokens()) {
      tokens.add(
          QueryAnalysisResponse.TokenInfo.builder()
              .token(token.token())
              .type(token.type())
              .position((int) token.position())
              .startOffset((int) token.startOffset())
              .endOffset((int) token.endOffset())
              .build());
    }

    return tokens;
  }

  private Map<String, List<String>> getTokenSynonymMapping(String text, String indexName)
      throws IOException {

    Map<String, List<String>> tokenSynonymMap = new HashMap<>();

    // 원본 토큰 추출 (동의어 확장 없이)
    AnalyzeRequest indexAnalyzeRequest =
        AnalyzeRequest.of(a -> a.index(indexName).analyzer("nori_index_analyzer").text(text));
    AnalyzeResponse indexResponse = elasticsearchClient.indices().analyze(indexAnalyzeRequest);

    // 검색 시 동의어 확장된 토큰 추출
    AnalyzeRequest searchAnalyzeRequest =
        AnalyzeRequest.of(a -> a.index(indexName).analyzer("nori_search_analyzer").text(text));
    AnalyzeResponse searchResponse = elasticsearchClient.indices().analyze(searchAnalyzeRequest);

    // position별로 토큰 그룹화
    Map<Integer, String> positionToOriginalToken = new HashMap<>();
    Map<Integer, List<String>> positionToExpandedTokens = new HashMap<>();

    // 원본 토큰 매핑
    for (AnalyzeToken token : indexResponse.tokens()) {
      int position = (int) token.position();
      positionToOriginalToken.putIfAbsent(position, token.token());
    }

    // 확장된 토큰 매핑
    for (AnalyzeToken token : searchResponse.tokens()) {
      int position = (int) token.position();
      positionToExpandedTokens.computeIfAbsent(position, k -> new ArrayList<>()).add(token.token());
    }

    // 각 position에서 원본과 다른 동의어들을 매핑
    for (Map.Entry<Integer, String> entry : positionToOriginalToken.entrySet()) {
      int position = entry.getKey();
      String originalToken = entry.getValue();
      List<String> expandedTokens = positionToExpandedTokens.get(position);

      if (expandedTokens != null && expandedTokens.size() > 1) {
        // 원본과 다른 토큰들만 동의어로 수집
        List<String> synonyms = new ArrayList<>();
        for (String expanded : expandedTokens) {
          if (!expanded.equals(originalToken)) {
            synonyms.add(expanded);
          }
        }
        if (!synonyms.isEmpty()) {
          tokenSynonymMap.put(originalToken, synonyms);
        }
      }
    }

    return tokenSynonymMap;
  }

  private List<QueryAnalysisResponse.UnitInfo> extractAndExpandUnits(String query) {
    // 원본 단위 추출
    List<String> extractedUnits = UnitExtractor.extractUnitsForSearch(query);

    // 각 단위에 대해 동의어 확장
    List<QueryAnalysisResponse.UnitInfo> unitInfos = new ArrayList<>();

    for (String unit : extractedUnits) {
      Set<String> expanded = UnitExtractor.expandUnitSynonyms(unit);

      unitInfos.add(
          QueryAnalysisResponse.UnitInfo.builder().original(unit).expanded(expanded).build());
    }

    return unitInfos;
  }

  private List<String> extractModels(String query, List<QueryAnalysisResponse.UnitInfo> unitInfos) {
    // 단위 목록 준비 (원본 단위만)
    List<String> units =
        unitInfos.stream()
            .map(QueryAnalysisResponse.UnitInfo::getOriginal)
            .collect(Collectors.toList());

    // 단위를 제외한 모델명 추출
    return ModelExtractor.extractModelsExcludingUnits(query, units);
  }
}
