package com.yjlee.search.search.analysis.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.search.analysis.dto.QueryAnalysisRequest;
import com.yjlee.search.search.analysis.dto.QueryAnalysisResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryAnalysisService {

  private final ElasticsearchClient elasticsearchClient;
  private final IndexEnvironmentRepository indexEnvironmentRepository;
  private final TempIndexService tempIndexService;
  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  // 동의어 경로 생성을 위한 내부 클래스
  private static class SynonymTokenInfo {
    String token;
    String type;
    int position;
    int positionLength;
    int startOffset;
    int endOffset;

    SynonymTokenInfo(
        String token,
        String type,
        int position,
        int positionLength,
        int startOffset,
        int endOffset) {
      this.token = token;
      this.type = type;
      this.position = position;
      this.positionLength = positionLength;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }
  }

  // 동의어 분석 결과를 담는 내부 클래스
  private static class SynonymAnalysisResult {
    String formattedTokens;
    Map<String, List<String>> synonymExpansions;

    SynonymAnalysisResult(String formattedTokens, Map<String, List<String>> synonymExpansions) {
      this.formattedTokens = formattedTokens;
      this.synonymExpansions = synonymExpansions;
    }
  }

  public QueryAnalysisResponse analyzeQuery(QueryAnalysisRequest request) {
    String query = request.getQuery();
    DictionaryEnvironmentType environment = request.getEnvironment();

    log.debug("쿼리 분석 시작 - 쿼리: {}, 환경: {}", query, environment);

    try {
      // 1. 인덱스 선택
      String indexName = getIndexNameForAnalysis(environment);

      // 2. Nori 형태소 분석
      QueryAnalysisResponse.NoriAnalysis noriAnalysis = analyzeWithNori(query, indexName);

      return QueryAnalysisResponse.builder()
          .environment(environment.name())
          .originalQuery(query)
          .noriAnalysis(noriAnalysis)
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

    // Low-level REST Client를 사용하여 원시 JSON 응답 가져오기
    Request request = new Request("GET", "/" + indexName + "/_analyze");
    String jsonBody =
        String.format(
            "{\"analyzer\":\"nori_search_analyzer\",\"text\":\"%s\",\"explain\":true}",
            text.replace("\"", "\\\""));
    request.setJsonEntity(jsonBody);

    Response response = restClient.performRequest(request);
    String responseBody = EntityUtils.toString(response.getEntity());
    JsonNode root = objectMapper.readTree(responseBody);

    List<QueryAnalysisResponse.TokenInfo> tokens = new ArrayList<>();
    String formattedTokens = "";
    SynonymAnalysisResult synonymResult = null;

    // detail 정보 처리
    JsonNode detail = root.get("detail");
    if (detail != null) {
      // tokenizer 단계의 토큰 정보 추출
      JsonNode tokenizer = detail.get("tokenizer");
      if (tokenizer != null && tokenizer.has("tokens")) {
        JsonNode tokenizerTokens = tokenizer.get("tokens");
        for (JsonNode token : tokenizerTokens) {
          tokens.add(
              QueryAnalysisResponse.TokenInfo.builder()
                  .token(token.get("token").asText())
                  .type(token.has("type") ? token.get("type").asText() : "word")
                  .position(token.get("position").asInt())
                  .startOffset(token.get("start_offset").asInt())
                  .endOffset(token.get("end_offset").asInt())
                  .build());
        }
      }

      // search_synonym_filter에서 토큰 포맷팅
      JsonNode tokenFilters = detail.get("tokenfilters");
      if (tokenFilters != null) {
        for (JsonNode filter : tokenFilters) {
          if ("search_synonym_filter".equals(filter.get("name").asText())) {
            JsonNode filterTokens = filter.get("tokens");
            if (filterTokens != null) {
              synonymResult = formatTokensWithSynonyms(filterTokens);
              formattedTokens = synonymResult.formattedTokens;
            }
            break;
          }
        }
      }
    }

    return QueryAnalysisResponse.NoriAnalysis.builder()
        .tokens(tokens)
        .formattedTokens(formattedTokens)
        .synonymExpansions(
            synonymResult != null ? synonymResult.synonymExpansions : new HashMap<>())
        .build();
  }

  private SynonymAnalysisResult formatTokensWithSynonyms(JsonNode tokens) {
    // Offset 쌍으로 토큰 그룹화 (원본 텍스트의 각 부분별 형태소 분석 결과)
    Map<String, List<SynonymTokenInfo>> tokensByOffset = new HashMap<>();

    for (JsonNode token : tokens) {
      int position = token.get("position").asInt();
      int positionLength = token.has("positionLength") ? token.get("positionLength").asInt() : 1;
      String type = token.has("type") ? token.get("type").asText() : "word";
      int startOffset = token.get("start_offset").asInt();
      int endOffset = token.get("end_offset").asInt();

      SynonymTokenInfo tokenInfo =
          new SynonymTokenInfo(
              token.get("token").asText(), type, position, positionLength, startOffset, endOffset);

      // Offset별 그룹화 (같은 offset = 원본 텍스트의 동일 부분)
      String offsetKey = startOffset + "-" + endOffset;
      tokensByOffset.computeIfAbsent(offsetKey, k -> new ArrayList<>()).add(tokenInfo);
    }

    // 원본 토큰들 추출 (type="word"인 토큰들을 offset 순서대로)
    List<String> baseTokens = new ArrayList<>();
    Map<String, List<String>> synonymExpansions = new HashMap<>();

    tokensByOffset.entrySet().stream()
        .sorted(Map.Entry.comparingByKey()) // offset 순서로 정렬
        .forEach(
            entry -> {
              List<SynonymTokenInfo> groupTokens = entry.getValue();

              // 원본 토큰 찾기 (type="word")
              String baseToken =
                  groupTokens.stream()
                      .filter(t -> "word".equals(t.type))
                      .findFirst()
                      .map(t -> t.token)
                      .orElse(null);

              if (baseToken != null) {
                baseTokens.add(baseToken);

                // 같은 offset 그룹의 SYNONYM 타입 토큰들 수집
                List<String> synonyms =
                    groupTokens.stream()
                        .filter(t -> "SYNONYM".equals(t.type))
                        .map(t -> t.token)
                        .distinct()
                        .collect(Collectors.toList());

                if (!synonyms.isEmpty()) {
                  synonymExpansions.put(baseToken, synonyms);
                }
              }
            });

    String baseForm = String.join(" ", baseTokens);

    // 각 offset 그룹별로 동의어 경로 생성
    List<String> allSynonymPaths = new ArrayList<>();

    for (Map.Entry<String, List<SynonymTokenInfo>> offsetGroup : tokensByOffset.entrySet()) {
      List<SynonymTokenInfo> groupTokens = offsetGroup.getValue();

      // Position별로 재그룹화 (이 offset 그룹 내에서)
      Map<Integer, List<SynonymTokenInfo>> positionMap = new HashMap<>();
      for (SynonymTokenInfo token : groupTokens) {
        positionMap.computeIfAbsent(token.position, k -> new ArrayList<>()).add(token);
      }

      // SYNONYM 타입이 있는 그룹만 처리
      boolean hasSynonym = groupTokens.stream().anyMatch(t -> "SYNONYM".equals(t.type));
      if (!hasSynonym) continue;

      // 이 offset 그룹에서 시작하는 모든 경로 탐색
      List<SynonymTokenInfo> startTokens =
          positionMap.values().stream()
              .flatMap(List::stream)
              .filter(
                  t ->
                      positionMap.keySet().stream()
                          .noneMatch(
                              pos ->
                                  pos + positionMap.get(pos).get(0).positionLength == t.position))
              .collect(Collectors.toList());

      // 시작 토큰이 명확하지 않으면 가장 작은 position에서 시작
      if (startTokens.isEmpty()) {
        int minPosition = positionMap.keySet().stream().min(Integer::compareTo).orElse(0);
        startTokens = positionMap.get(minPosition);
      }

      // 각 시작점에서 경로 탐색
      for (SynonymTokenInfo startToken : startTokens) {
        if ("SYNONYM".equals(startToken.type)) {
          List<String> paths = exploreOffsetPath(startToken, positionMap);
          allSynonymPaths.addAll(paths);
        }
      }
    }

    // 중복 제거 및 원본과 같은 경로 제외
    List<String> uniquePaths =
        allSynonymPaths.stream()
            .distinct()
            .filter(path -> !path.equals(baseForm))
            .collect(Collectors.toList());

    // 포맷팅
    String formattedTokens;
    if (uniquePaths.isEmpty()) {
      formattedTokens = baseForm;
    } else {
      formattedTokens = baseForm + "{" + String.join("|", uniquePaths) + "}";
    }

    return new SynonymAnalysisResult(formattedTokens, synonymExpansions);
  }

  private List<String> exploreOffsetPath(
      SynonymTokenInfo currentToken, Map<Integer, List<SynonymTokenInfo>> positionMap) {

    List<String> paths = new ArrayList<>();
    int nextPosition = currentToken.position + currentToken.positionLength;

    // 다음 position에 토큰이 없으면 현재 토큰으로 경로 종료
    if (!positionMap.containsKey(nextPosition)) {
      paths.add(currentToken.token);
      return paths;
    }

    // 다음 position의 모든 토큰에 대해 재귀적으로 경로 탐색
    List<SynonymTokenInfo> nextTokens = positionMap.get(nextPosition);
    for (SynonymTokenInfo nextToken : nextTokens) {
      if ("SYNONYM".equals(nextToken.type)) {
        List<String> subPaths = exploreOffsetPath(nextToken, positionMap);
        for (String subPath : subPaths) {
          paths.add(currentToken.token + " " + subPath);
        }
      }
    }

    // 다음 토큰이 모두 SYNONYM이 아니면 현재 토큰으로 종료
    if (paths.isEmpty()) {
      paths.add(currentToken.token);
    }

    return paths;
  }

}
