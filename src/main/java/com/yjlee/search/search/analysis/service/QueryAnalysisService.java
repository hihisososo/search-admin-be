package com.yjlee.search.search.analysis.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    SynonymTokenInfo(String token, String type, int position, int positionLength) {
      this.token = token;
      this.type = type;
      this.position = position;
      this.positionLength = positionLength;
    }
  }

  // 동의어 경로를 나타내는 클래스
  private static class SynonymPath {
    private final List<SynonymTokenInfo> tokens;

    public SynonymPath() {
      this.tokens = new ArrayList<>();
    }

    public SynonymPath(SynonymPath other) {
      this.tokens = new ArrayList<>(other.tokens);
    }

    public void addToken(SynonymTokenInfo token) {
      tokens.add(token);
    }

    public List<SynonymTokenInfo> getTokens() {
      return new ArrayList<>(tokens);
    }

    public SynonymTokenInfo getLastToken() {
      return tokens.get(tokens.size() - 1);
    }

    public String getFullText() {
      return tokens.stream().map(t -> t.token).collect(Collectors.joining(" "));
    }

    public int getEndPosition() {
      SynonymTokenInfo last = getLastToken();
      return last.position + last.positionLength;
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
    List<String> synonymPaths = new ArrayList<>();

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

      // search_synonym_filter에서 동의어 경로 추출
      JsonNode tokenFilters = detail.get("tokenfilters");
      if (tokenFilters != null) {
        for (JsonNode filter : tokenFilters) {
          if ("search_synonym_filter".equals(filter.get("name").asText())) {
            JsonNode filterTokens = filter.get("tokens");
            if (filterTokens != null) {
              synonymPaths = extractSynonymPathsFromJson(filterTokens);
            }
            break;
          }
        }
      }
    }

    return QueryAnalysisResponse.NoriAnalysis.builder()
        .tokens(tokens)
        .synonymPaths(synonymPaths)
        .build();
  }

  private List<String> extractSynonymPathsFromJson(JsonNode tokens) {
    // Position별로 토큰 그룹화
    Map<Integer, List<SynonymTokenInfo>> tokensByPosition = new HashMap<>();

    for (JsonNode token : tokens) {
      int position = token.get("position").asInt();
      // positionLength 정보를 JSON에서 직접 추출
      int positionLength = token.has("positionLength") ? token.get("positionLength").asInt() : 1;
      String type = token.has("type") ? token.get("type").asText() : "word";

      tokensByPosition
          .computeIfAbsent(position, k -> new ArrayList<>())
          .add(new SynonymTokenInfo(token.get("token").asText(), type, position, positionLength));
    }

    // 경로 생성 - SynonymFilterParserTest의 로직 사용
    List<SynonymPath> completePaths = new ArrayList<>();
    List<SynonymTokenInfo> startTokens = tokensByPosition.getOrDefault(0, new ArrayList<>());

    for (SynonymTokenInfo startToken : startTokens) {
      SynonymPath path = new SynonymPath();
      path.addToken(startToken);
      explorePath(path, tokensByPosition, completePaths);
    }

    // 경로를 문자열 리스트로 변환
    return completePaths.stream()
        .map(SynonymPath::getFullText)
        .distinct()
        .collect(Collectors.toList());
  }

  private void explorePath(
      SynonymPath currentPath,
      Map<Integer, List<SynonymTokenInfo>> tokensByPosition,
      List<SynonymPath> completePaths) {

    SynonymTokenInfo lastToken = currentPath.getLastToken();
    int nextPosition = lastToken.position + lastToken.positionLength;

    // 다음 포지션에 토큰이 없으면 경로 완성
    if (!tokensByPosition.containsKey(nextPosition)) {
      // SYNONYM 타입이 포함된 경로만 추가
      boolean hasSynonym = currentPath.getTokens().stream().anyMatch(t -> "SYNONYM".equals(t.type));

      if (hasSynonym) {
        completePaths.add(new SynonymPath(currentPath));
      }
      return;
    }

    // 다음 포지션의 토큰들로 경로 확장
    List<SynonymTokenInfo> nextTokens = tokensByPosition.get(nextPosition);
    for (SynonymTokenInfo nextToken : nextTokens) {
      SynonymPath newPath = new SynonymPath(currentPath);
      newPath.addToken(nextToken);
      explorePath(newPath, tokensByPosition, completePaths);
    }
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
