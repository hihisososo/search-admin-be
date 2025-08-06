package com.yjlee.search.dictionary.user.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchAnalyzeService {

  private final ElasticsearchClient elasticsearchClient;

  public List<AnalyzeTextResponse.TokenInfo> analyzeText(
      String text, DictionaryEnvironmentType environment) {
    try {
      String indexName = getIndexName(environment);

      AnalyzeRequest analyzeRequest =
          AnalyzeRequest.of(a -> a.index(indexName).field("name_nori").text(text));

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

  private String getIndexName(DictionaryEnvironmentType environment) {
    switch (environment) {
      case DEV:
        return "products-dev";
      case PROD:
        return "products-prod";
      default:
        return "products-dev";
    }
  }
}
