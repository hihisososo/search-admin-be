package com.yjlee.search.analysis.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.yjlee.search.analysis.model.TokenEdge;
import com.yjlee.search.analysis.model.TokenGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenGraphParserTest {

  private TokenGraphParser tokenGraphParser;

  @BeforeEach
  void setUp() {
    tokenGraphParser = new TokenGraphParser();
  }

  @Test
  @DisplayName("동의어 응답 파싱")
  void shouldParseSynonymResponse() throws IOException {

    String jsonResponse = loadTestResponse("synonym-response.json");

    TokenGraph result = tokenGraphParser.parse(jsonResponse, "삼성전자", "search_synonym_filter");
    List<String> tokens = result.getEdges().stream().map(TokenEdge::getToken).collect(Collectors.toList());
    List<String> synonyms = result.getEdges().stream()
        .filter(TokenEdge::isSynonym)
        .map(TokenEdge::getToken)
        .collect(Collectors.toList());

    assertThat(result.getOriginalQuery()).isEqualTo("삼성전자");
    assertThat(result.getEdges()).hasSize(3);
    assertThat(tokens).containsExactlyInAnyOrder("삼성", "samsung", "전자");
    assertThat(synonyms).containsExactly("samsung");
  }

  @Test
  @DisplayName("복합어 처리")
  void shouldParseMultiPositionToken() throws IOException {
    String jsonResponse = loadTestResponse("compound-word-response.json");

    TokenGraph result = tokenGraphParser.parse(jsonResponse, "갤럭시북", "search_synonym_filter");
    TokenEdge compoundToken = result.getEdges().stream()
        .filter(e -> e.getToken().equals("갤럭시북"))
        .findFirst()
        .orElseThrow();

    assertThat(compoundToken.getPositionLength()).isEqualTo(2);
    assertThat(compoundToken.isMultiPosition()).isTrue();
    assertThat(result.getEdges()).hasSize(3);
    assertThat(compoundToken.getFromPosition()).isEqualTo(0);
    assertThat(compoundToken.getToPosition()).isEqualTo(2);
  }

  @Test
  @DisplayName("추가 토큰 파싱")
  void shouldParseAdditionalTokens() throws IOException {
    String jsonResponse = loadTestResponse("additional-tokens-response.json");

    TokenGraph result = tokenGraphParser.parse(jsonResponse, "갤럭시북", "stopword_filter");

    List<String> additionalTokens = result.getEdges().stream()
        .filter(e -> "additional".equals(e.getType()))
        .map(TokenEdge::getToken)
        .collect(Collectors.toList());

    assertThat(result.getEdges()).hasSize(3);
    assertThat(additionalTokens).containsExactly("galaxy", "notebook");
  }

  @Test
  @DisplayName("빈 detail 응답")
  void shouldHandleEmptyDetail() throws IOException {
    String jsonResponse = loadTestResponse("empty-detail-response.json");

    TokenGraph result = tokenGraphParser.parse(jsonResponse, "테스트", "search_synonym_filter");

    assertThat(result.getEdges()).isEmpty();
    assertThat(result.getOriginalQuery()).isEqualTo("테스트");
  }

  @Test
  @DisplayName("복잡한 실제 ES 응답 파싱")
  void shouldParseComplexRealResponse() throws IOException {
    String jsonResponse = loadTestResponse("complex-real-response.json");

    TokenGraph result = tokenGraphParser.parse(jsonResponse, "SK하이닉스 DDR5", "search_synonym_filter");
    TokenEdge compoundToken = result.getEdges().stream()
        .filter(e -> e.getToken().equals("sk하이닉스"))
        .findFirst()
        .orElseThrow();
    TokenEdge synonymToken = result.getEdges().stream()
        .filter(e -> e.getToken().equals("hynix"))
        .findFirst()
        .orElseThrow();
    assertThat(synonymToken.isSynonym()).isTrue();
    result.generatePaths();

    assertThat(result.getEdges()).hasSize(5);
    assertThat(result.getPaths()).isNotEmpty();
    assertThat(compoundToken.getPositionLength()).isEqualTo(2);
  }

  private String loadTestResponse(String fileName) throws IOException {
    Path path = Paths.get("src/test/resources/analysis-responses/" + fileName);
    return Files.readString(path);
  }
}
