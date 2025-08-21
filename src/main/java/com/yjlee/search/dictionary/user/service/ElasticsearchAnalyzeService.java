package com.yjlee.search.dictionary.user.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchAnalyzeService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentRepository indexEnvironmentRepository;

  public List<AnalyzeTextResponse.TokenInfo> analyzeText(
      String text, DictionaryEnvironmentType environment) {
    try {
      String indexName = getIndexName(environment);

      AnalyzeRequest analyzeRequest =
          AnalyzeRequest.of(a -> a.index(indexName).analyzer("nori_index_analyzer").text(text));

      AnalyzeResponse response = elasticsearchClient.indices().analyze(analyzeRequest);

      List<AnalyzeTextResponse.TokenInfo> tokens = new ArrayList<>();
      for (AnalyzeToken token : response.tokens()) {
        tokens.add(
            AnalyzeTextResponse.TokenInfo.builder()
                .token(token.token())
                .type(token.type())
                .position((int) token.position())
                .startOffset((int) token.startOffset())
                .endOffset((int) token.endOffset())
                .positionLengthTags(new ArrayList<>())
                .build());
      }

      return tokens;
    } catch (IOException e) {
      log.error("형태소 분석 중 오류 발생", e);
      throw new RuntimeException("형태소 분석 실패", e);
    }
  }

  public Set<String> getExpandedSynonyms(String text, DictionaryEnvironmentType environment) {
    try {
      String indexName = getIndexName(environment);

      AnalyzeRequest analyzeRequest =
          AnalyzeRequest.of(a -> a.index(indexName).analyzer("nori_search_analyzer").text(text));

      AnalyzeResponse response = elasticsearchClient.indices().analyze(analyzeRequest);

      // position별로 토큰 그룹화
      Map<Integer, List<String>> tokensByPosition = new HashMap<>();
      for (AnalyzeToken token : response.tokens()) {
        tokensByPosition
            .computeIfAbsent((int) token.position(), k -> new ArrayList<>())
            .add(token.token());
      }

      // 같은 position에 2개 이상 토큰이 있으면 동의어로 확장된 것
      Set<String> expandedSynonyms = new LinkedHashSet<>();
      for (List<String> tokens : tokensByPosition.values()) {
        if (tokens.size() > 1) {
          expandedSynonyms.addAll(tokens);
        }
      }

      return expandedSynonyms;
    } catch (IOException e) {
      log.error("동의어 확장 분석 중 오류 발생", e);
      return new LinkedHashSet<>();
    }
  }

  private String getIndexName(DictionaryEnvironmentType environment) {
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
