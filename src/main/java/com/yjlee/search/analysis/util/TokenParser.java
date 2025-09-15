package com.yjlee.search.analysis.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.analysis.domain.TokenInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
public final class TokenParser {

  private TokenParser() {
  }
  private static final String DETAIL = "detail";
  private static final String TOKEN_FILTERS = "tokenfilters";
  private static final String NAME = "name";
  private static final String TOKENS = "tokens";
  private static final String POSITION = "position";
  private static final String POSITION_LENGTH = "positionLength";
  private static final String TYPE = "type";
  private static final String START_OFFSET = "start_offset";
  private static final String END_OFFSET = "end_offset";
  private static final String TOKEN = "token";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static List<TokenInfo> parse(String jsonResponse, String targetFilter) {
    try {
      JsonNode root = objectMapper.readTree(jsonResponse);
      List<TokenInfo> tokens = new ArrayList<>();

      JsonNode detail = root.get(DETAIL);
      if (detail == null) {
        return tokens;
      }

      JsonNode tokenFilters = detail.get(TOKEN_FILTERS);
      if (tokenFilters == null) {
        return tokens;
      }

      tokenFilters.forEach(
          filter -> {
            if (targetFilter.equals(filter.get(NAME).asText())) {
              JsonNode tokensNode = filter.get(TOKENS);
              if (tokensNode != null) {
                tokensNode.forEach(token -> tokens.add(createTokenInfo(token)));
              }
            }
          });

      return tokens;
    } catch (IOException e) {
      throw new RuntimeException("ES analyze 응답 파싱 실패", e);
    }
  }

  private static TokenInfo createTokenInfo(JsonNode token) {
    return TokenInfo.builder()
        .token(token.get(TOKEN).asText())
        .type(token.get(TYPE).asText())
        .position(token.get(POSITION).asInt())
        .positionLength(token.get(POSITION_LENGTH).asInt())
        .startOffset(token.get(START_OFFSET).asInt())
        .endOffset(token.get(END_OFFSET).asInt())
        .build();
  }
}