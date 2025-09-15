package com.yjlee.search.analysis.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yjlee.search.analysis.domain.TokenInfo;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenParserTest {

  @Test
  @DisplayName("실제 ES analyze 응답 파싱 - 일반 토큰")
  void shouldParseNormalTokens() {
    String jsonResponse = """
        {
          "detail": {
            "tokenfilters": [
              {
                "name": "search_filter",
                "tokens": [
                  {
                    "token": "아이폰",
                    "type": "word",
                    "position": 0,
                    "positionLength": 1,
                    "start_offset": 0,
                    "end_offset": 3
                  },
                  {
                    "token": "15",
                    "type": "word",
                    "position": 1,
                    "positionLength": 1,
                    "start_offset": 4,
                    "end_offset": 6
                  },
                  {
                    "token": "프로",
                    "type": "word",
                    "position": 2,
                    "positionLength": 1,
                    "start_offset": 7,
                    "end_offset": 9
                  }
                ]
              }
            ]
          }
        }
        """;

    List<TokenInfo> tokens = TokenParser.parse(jsonResponse, "search_filter");

    assertThat(tokens).hasSize(3);

    TokenInfo firstToken = tokens.get(0);
    assertThat(firstToken.getToken()).isEqualTo("아이폰");
    assertThat(firstToken.getType()).isEqualTo("word");
    assertThat(firstToken.getPosition()).isEqualTo(0);
    assertThat(firstToken.getPositionLength()).isEqualTo(1);
    assertThat(firstToken.getStartOffset()).isEqualTo(0);
    assertThat(firstToken.getEndOffset()).isEqualTo(3);
    assertThat(firstToken.isWord()).isTrue();
    assertThat(firstToken.isSynonym()).isFalse();

    TokenInfo secondToken = tokens.get(1);
    assertThat(secondToken.getToken()).isEqualTo("15");
    assertThat(secondToken.getPosition()).isEqualTo(1);
  }

  @Test
  @DisplayName("빈 토큰 리스트 처리")
  void shouldHandleEmptyTokenList() {
    String jsonResponse = """
        {
          "detail": {
            "tokenfilters": [
              {
                "name": "search_filter",
                "tokens": []
              }
            ]
          }
        }
        """;

    List<TokenInfo> tokens = TokenParser.parse(jsonResponse, "search_filter");

    assertThat(tokens).isEmpty();
  }

  @Test
  @DisplayName("잘못된 JSON")
  void shouldThrowExceptionForInvalidJson() {
    String invalidJson = "{ invalid json }";

    assertThatThrownBy(() -> TokenParser.parse(invalidJson, "search_filter"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("복잡한 실제 응답 파싱")
  void shouldParseComplexRealResponse() {
    String jsonResponse = """
        {
          "detail": {
            "tokenfilters": [
              {
                "name": "search_filter",
                "tokens": [
                  {
                    "token": "삼성",
                    "type": "word",
                    "position": 0,
                    "positionLength": 1,
                    "start_offset": 0,
                    "end_offset": 2
                  },
                  {
                    "token": "samsung",
                    "type": "SYNONYM",
                    "position": 0,
                    "positionLength": 1,
                    "start_offset": 0,
                    "end_offset": 2
                  },
                  {
                    "token": "갤럭시",
                    "type": "word",
                    "position": 1,
                    "positionLength": 2,
                    "start_offset": 3,
                    "end_offset": 6
                  },
                  {
                    "token": "galaxy",
                    "type": "SYNONYM",
                    "position": 1,
                    "positionLength": 2,
                    "start_offset": 3,
                    "end_offset": 6
                  },
                  {
                    "token": "s24",
                    "type": "word",
                    "position": 3,
                    "positionLength": 1,
                    "start_offset": 7,
                    "end_offset": 10
                  },
                  {
                    "token": "울트라",
                    "type": "word",
                    "position": 4,
                    "positionLength": 1,
                    "start_offset": 11,
                    "end_offset": 14
                  },
                  {
                    "token": "ultra",
                    "type": "SYNONYM",
                    "position": 4,
                    "positionLength": 1,
                    "start_offset": 11,
                    "end_offset": 14
                  }
                ]
              }
            ]
          }
        }
        """;

    List<TokenInfo> tokens = TokenParser.parse(jsonResponse, "search_filter");

    assertThat(tokens).hasSize(7);

    long wordCount = tokens.stream().filter(TokenInfo::isWord).count();
    long synonymCount = tokens.stream().filter(TokenInfo::isSynonym).count();
    long multiPosCount = tokens.stream().filter(TokenInfo::isMultiPosition).count();

    assertThat(wordCount).isEqualTo(4);
    assertThat(synonymCount).isEqualTo(3);
    assertThat(multiPosCount).isEqualTo(2);
  }
}