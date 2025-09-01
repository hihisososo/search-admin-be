package com.yjlee.search.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SynonymFilterParserTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static void main(String[] args) throws IOException {
    String jsonInput =
        """
             {
        "name": "search_synonym_filter",
        "tokens": [
          {
            "token": "pc",
            "start_offset": 0,
            "end_offset": 6,
            "type": "SYNONYM",
            "position": 0,
            "positionLength": 7,
            "bytes": "[70 63]",
            "leftPOS": null,
            "morphemes": null,
            "posType": null,
            "reading": null,
            "rightPOS": null,
            "termFrequency": 1
          },
          {
            "token": "데스크톱",
            "start_offset": 0,
            "end_offset": 6,
            "type": "SYNONYM",
            "position": 0,
            "positionLength": 7,
            "bytes": "[eb 8d b0 ec 8a a4 ed 81 ac ed 86 b1]",
            "leftPOS": null,
            "morphemes": null,
            "posType": null,
            "reading": null,
            "rightPOS": null,
            "termFrequency": 1
          },
          {
            "token": "데스크",
            "start_offset": 0,
            "end_offset": 6,
            "type": "SYNONYM",
            "position": 0,
            "bytes": "[eb 8d b0 ec 8a a4 ed 81 ac]",
            "leftPOS": null,
            "morphemes": null,
            "posType": null,
            "positionLength": 1,
            "reading": null,
            "rightPOS": null,
            "termFrequency": 1
          },
          {
            "token": "제로",
            "start_offset": 0,
            "end_offset": 6,
            "type": "SYNONYM",
            "position": 0,
            "positionLength": 2,
            "bytes": "[ec a0 9c eb a1 9c]",
            "leftPOS": null,
            "morphemes": null,
            "posType": null,
            "reading": null,
            "rightPOS": null,
            "termFrequency": 1
          },
          {
            "token": "제로",
            "start_offset": 0,
            "end_offset": 2,
            "type": "word",
            "position": 0,
            "positionLength": 5,
            "bytes": "[ec a0 9c eb a1 9c]",
            "leftPOS": "NNG(General Noun)",
            "morphemes": null,
            "posType": "MORPHEME",
            "reading": null,
            "rightPOS": "NNG(General Noun)",
            "termFrequency": 1
          },
          {
            "token": "탑",
            "start_offset": 0,
            "end_offset": 6,
            "type": "SYNONYM",
            "position": 1,
            "positionLength": 6,
            "bytes": "[ed 83 91]",
            "leftPOS": null,
            "morphemes": null,
            "posType": null,
            "reading": null,
            "rightPOS": null,
            "termFrequency": 1
          },
          {
            "token": "음료",
            "start_offset": 0,
            "end_offset": 6,
            "type": "SYNONYM",
            "position": 2,
            "bytes": "[ec 9d 8c eb a3 8c]",
            "leftPOS": null,
            "morphemes": null,
            "posType": null,
            "positionLength": 1,
            "reading": null,
            "rightPOS": null,
            "termFrequency": 1
          },
          {
            "token": "필터",
            "start_offset": 0,
            "end_offset": 6,
            "type": "SYNONYM",
            "position": 3,
            "bytes": "[ed 95 84 ed 84 b0]",
            "leftPOS": null,
            "morphemes": null,
            "posType": null,
            "positionLength": 1,
            "reading": null,
            "rightPOS": null,
            "termFrequency": 1
          },
          {
            "token": "쿼리",
            "start_offset": 0,
            "end_offset": 6,
            "type": "SYNONYM",
            "position": 4,
            "positionLength": 3,
            "bytes": "[ec bf bc eb a6 ac]",
            "leftPOS": null,
            "morphemes": null,
            "posType": null,
            "reading": null,
            "rightPOS": null,
            "termFrequency": 1
          },
          {
            "token": "음료",
            "start_offset": 2,
            "end_offset": 4,
            "type": "word",
            "position": 5,
            "bytes": "[ec 9d 8c eb a3 8c]",
            "leftPOS": "NNG(General Noun)",
            "morphemes": null,
            "posType": "MORPHEME",
            "positionLength": 1,
            "reading": null,
            "rightPOS": "NNG(General Noun)",
            "termFrequency": 1
          },
          {
            "token": "필터",
            "start_offset": 4,
            "end_offset": 6,
            "type": "word",
            "position": 6,
            "bytes": "[ed 95 84 ed 84 b0]",
            "leftPOS": "NNG(General Noun)",
            "morphemes": null,
            "posType": "MORPHEME",
            "positionLength": 1,
            "reading": null,
            "rightPOS": "NNG(General Noun)",
            "termFrequency": 1
          }
        ]
      }
            """;

    List<SynonymPath> paths = parseSynonymFilter(jsonInput);

    System.out.println("원본 쿼리: pc");
    System.out.println("=====================================");

    for (int i = 0; i < paths.size(); i++) {
      SynonymPath path = paths.get(i);
      System.out.println(
          "경로 "
              + (i + 1)
              + ": "
              + path.getTokens().stream().map(Token::getToken).collect(Collectors.joining(" → ")));
      System.out.println("  - 전체 텍스트: " + path.getFullText());
      System.out.println("  - 포지션 범위: 0 ~ " + path.getEndPosition());
      System.out.println();
    }

    System.out.println("=====================================");
    System.out.println("총 " + paths.size() + "개의 경로 발견");
  }

  private static List<SynonymPath> parseSynonymFilter(String jsonInput) throws IOException {
    JsonNode root = objectMapper.readTree(jsonInput);
    JsonNode tokensNode = root.get("tokens");

    List<Token> tokens = new ArrayList<>();
    for (JsonNode tokenNode : tokensNode) {
      tokens.add(
          new Token(
              tokenNode.get("token").asText(),
              tokenNode.get("position").asInt(),
              tokenNode.get("positionLength").asInt()));
    }

    return buildPaths(tokens);
  }

  private static List<SynonymPath> buildPaths(List<Token> tokens) {
    Map<Integer, List<Token>> tokensByPosition =
        tokens.stream().collect(Collectors.groupingBy(Token::getPosition));

    List<SynonymPath> completePaths = new ArrayList<>();

    List<Token> startTokens = tokensByPosition.getOrDefault(0, new ArrayList<>());

    for (Token startToken : startTokens) {
      SynonymPath path = new SynonymPath();
      path.addToken(startToken);

      explorePath(path, tokensByPosition, completePaths);
    }

    return completePaths;
  }

  private static void explorePath(
      SynonymPath currentPath,
      Map<Integer, List<Token>> tokensByPosition,
      List<SynonymPath> completePaths) {
    Token lastToken = currentPath.getLastToken();
    int nextPosition = lastToken.getPosition() + lastToken.getPositionLength();

    if (!tokensByPosition.containsKey(nextPosition)) {
      completePaths.add(new SynonymPath(currentPath));
      return;
    }

    List<Token> nextTokens = tokensByPosition.get(nextPosition);
    for (Token nextToken : nextTokens) {
      SynonymPath newPath = new SynonymPath(currentPath);
      newPath.addToken(nextToken);
      explorePath(newPath, tokensByPosition, completePaths);
    }
  }

  static class Token {
    private final String token;
    private final int position;
    private final int positionLength;

    public Token(String token, int position, int positionLength) {
      this.token = token;
      this.position = position;
      this.positionLength = positionLength;
    }

    public String getToken() {
      return token;
    }

    public int getPosition() {
      return position;
    }

    public int getPositionLength() {
      return positionLength;
    }

    @Override
    public String toString() {
      return String.format("Token{token='%s', pos=%d, len=%d}", token, position, positionLength);
    }
  }

  static class SynonymPath {
    private final List<Token> tokens;

    public SynonymPath() {
      this.tokens = new ArrayList<>();
    }

    public SynonymPath(SynonymPath other) {
      this.tokens = new ArrayList<>(other.tokens);
    }

    public void addToken(Token token) {
      tokens.add(token);
    }

    public List<Token> getTokens() {
      return new ArrayList<>(tokens);
    }

    public Token getLastToken() {
      return tokens.get(tokens.size() - 1);
    }

    public String getFullText() {
      return tokens.stream().map(Token::getToken).collect(Collectors.joining(" "));
    }

    public int getEndPosition() {
      Token last = getLastToken();
      return last.getPosition() + last.getPositionLength();
    }

    @Override
    public String toString() {
      return "Path: " + getFullText();
    }
  }
}
