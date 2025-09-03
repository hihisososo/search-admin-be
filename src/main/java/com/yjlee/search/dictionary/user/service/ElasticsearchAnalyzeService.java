package com.yjlee.search.dictionary.user.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<AnalyzeTextResponse.TokenInfo> analyzeText(
      String text, DictionaryEnvironmentType environment) {
    try {
      String indexName = getIndexName(environment);

      // explain: true를 추가하여 상세한 분석 정보 요청
      AnalyzeRequest analyzeRequest =
          AnalyzeRequest.of(
              a -> a.index(indexName).analyzer("nori_search_analyzer").text(text).explain(true));

      AnalyzeResponse response = elasticsearchClient.indices().analyze(analyzeRequest);

      // explain 응답에서 토큰 정보 추출
      return extractTokensFromExplainResponse(response);
    } catch (IOException e) {
      log.error("형태소 분석 중 오류 발생", e);
      throw new RuntimeException("형태소 분석 실패", e);
    }
  }

  private List<AnalyzeTextResponse.TokenInfo> extractTokensFromExplainResponse(
      AnalyzeResponse response) {
    List<AnalyzeTextResponse.TokenInfo> tokens = new ArrayList<>();

    // explain이 없는 경우 기본 처리
    if (response.detail() == null) {
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
    }

    // detail에서 stopword_filter의 토큰 추출
    var detail = response.detail();
    if (detail.tokenfilters() != null) {
      for (var filter : detail.tokenfilters()) {
        if ("stopword_filter".equals(filter.name())) {
          if (filter.tokens() != null) {
            for (var token : filter.tokens()) {
              // ExplainAnalyzeToken에서 직접 필드 접근
              tokens.add(
                  AnalyzeTextResponse.TokenInfo.builder()
                      .token(token.token())
                      .type(token.type())
                      .position((int) token.position())
                      .startOffset((int) token.startOffset())
                      .endOffset((int) token.endOffset())
                      .positionLength(extractAttributeInteger(token, "positionLength"))
                      .leftPOS(extractAttributeString(token, "leftPOS"))
                      .rightPOS(extractAttributeString(token, "rightPOS"))
                      .posType(extractAttributeString(token, "posType"))
                      .isSynonym("SYNONYM".equals(token.type()))
                      .positionLengthTags(new ArrayList<>())
                      .build());
            }
          }
          break; // stopword_filter 찾았으면 종료
        }
      }
    }

    // stopword_filter가 없으면 기본 토큰 사용
    if (tokens.isEmpty() && response.tokens() != null) {
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
    }

    return tokens;
  }

  private String extractAttributeString(
      co.elastic.clients.elasticsearch.indices.analyze.ExplainAnalyzeToken token,
      String attributeName) {
    // ExplainAnalyzeToken의 attributes 맵에서 값 추출
    if (token.attributes() != null && token.attributes().containsKey(attributeName)) {
      var attr = token.attributes().get(attributeName);
      if (attr != null) {
        // JsonData를 String으로 변환
        return attr.toString().replaceAll("^\"|\"$", ""); // 따옴표 제거
      }
    }
    return null;
  }

  private Integer extractAttributeInteger(
      co.elastic.clients.elasticsearch.indices.analyze.ExplainAnalyzeToken token,
      String attributeName) {
    // ExplainAnalyzeToken의 attributes 맵에서 값 추출
    if (token.attributes() != null && token.attributes().containsKey(attributeName)) {
      var attr = token.attributes().get(attributeName);
      if (attr != null) {
        try {
          // JsonData를 Integer로 변환
          String strValue = attr.toString().replaceAll("^\"|\"$", "");
          return Integer.parseInt(strValue);
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }
    return null;
  }

  public Set<String> getExpandedSynonyms(String text, DictionaryEnvironmentType environment) {
    try {
      String indexName = getIndexName(environment);

      // explain: true를 추가하여 상세한 분석 정보 요청
      AnalyzeRequest analyzeRequest =
          AnalyzeRequest.of(
              a -> a.index(indexName).analyzer("nori_search_analyzer").text(text).explain(true));

      AnalyzeResponse response = elasticsearchClient.indices().analyze(analyzeRequest);

      // explain 응답에서 동의어 추출
      Set<String> expandedSynonyms = new LinkedHashSet<>();
      List<AnalyzeTextResponse.TokenInfo> tokens = extractTokensFromExplainResponse(response);

      // position별로 토큰 그룹화
      Map<Integer, List<AnalyzeTextResponse.TokenInfo>> tokensByPosition = new HashMap<>();
      for (AnalyzeTextResponse.TokenInfo token : tokens) {
        tokensByPosition.computeIfAbsent(token.getPosition(), k -> new ArrayList<>()).add(token);
      }

      // 같은 position에 여러 토큰이 있거나 type이 SYNONYM인 경우 동의어로 추출
      for (Map.Entry<Integer, List<AnalyzeTextResponse.TokenInfo>> entry :
          tokensByPosition.entrySet()) {
        List<AnalyzeTextResponse.TokenInfo> posTokens = entry.getValue();
        if (posTokens.size() > 1) {
          // 같은 position에 여러 토큰이 있는 경우
          for (AnalyzeTextResponse.TokenInfo token : posTokens) {
            expandedSynonyms.add(token.getToken());
          }
        } else if (posTokens.size() == 1 && posTokens.get(0).isSynonym()) {
          // type이 SYNONYM인 경우
          expandedSynonyms.add(posTokens.get(0).getToken());
        }
      }

      return expandedSynonyms;
    } catch (IOException e) {
      log.error("동의어 확장 분석 중 오류 발생", e);
      return new LinkedHashSet<>();
    }
  }

  /** 토큰별 동의어 매핑을 반환합니다. 예: "삼성전자" -> {"삼성": ["samsung"], "전자": ["electronics"]} */
  public Map<String, List<String>> getTokenSynonymsMapping(
      String text, DictionaryEnvironmentType environment) {
    Map<String, List<String>> tokenSynonymMap = new HashMap<>();

    try {
      String indexName = getIndexName(environment);

      // explain 모드로 상세 분석 정보 추출
      AnalyzeRequest searchAnalyzeRequest =
          AnalyzeRequest.of(
              a -> a.index(indexName).analyzer("nori_search_analyzer").text(text).explain(true));
      AnalyzeResponse searchResponse = elasticsearchClient.indices().analyze(searchAnalyzeRequest);

      List<AnalyzeTextResponse.TokenInfo> tokens = extractTokensFromExplainResponse(searchResponse);

      // position별로 토큰 그룹화
      Map<Integer, List<AnalyzeTextResponse.TokenInfo>> tokensByPosition = new HashMap<>();
      for (AnalyzeTextResponse.TokenInfo token : tokens) {
        tokensByPosition.computeIfAbsent(token.getPosition(), k -> new ArrayList<>()).add(token);
      }

      // 각 position에서 동의어 매핑 생성
      for (Map.Entry<Integer, List<AnalyzeTextResponse.TokenInfo>> entry :
          tokensByPosition.entrySet()) {
        List<AnalyzeTextResponse.TokenInfo> posTokens = entry.getValue();

        if (posTokens.size() > 1) {
          // 같은 position에 여러 토큰이 있는 경우
          // SYNONYM이 아닌 토큰을 원본으로, SYNONYM인 토큰을 동의어로 처리
          AnalyzeTextResponse.TokenInfo originalToken = null;
          List<String> synonyms = new ArrayList<>();

          for (AnalyzeTextResponse.TokenInfo token : posTokens) {
            if (!token.isSynonym() && originalToken == null) {
              originalToken = token;
            } else if (token.isSynonym()) {
              synonyms.add(token.getToken());
            }
          }

          // 원본 토큰이 있고 동의어가 있는 경우 매핑 추가
          if (originalToken != null && !synonyms.isEmpty()) {
            tokenSynonymMap.put(originalToken.getToken(), synonyms);
          }
          // 모든 토큰이 동의어인 경우 (positionLength=2인 경우)
          else if (originalToken == null && posTokens.size() > 1) {
            // 첫 번째 토큰을 원본으로, 나머지를 동의어로 처리
            String firstToken = posTokens.get(0).getToken();
            List<String> otherTokens = new ArrayList<>();
            for (int i = 1; i < posTokens.size(); i++) {
              otherTokens.add(posTokens.get(i).getToken());
            }
            if (!otherTokens.isEmpty()) {
              tokenSynonymMap.put(firstToken, otherTokens);
            }
          }
        }
      }

      return tokenSynonymMap;
    } catch (IOException e) {
      log.error("토큰별 동의어 매핑 생성 중 오류 발생", e);
      return new HashMap<>();
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
