package com.yjlee.search.analysis.domain;

import static com.yjlee.search.common.constants.ESFields.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TokenGraphParser {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public TokenGraph parse(String jsonResponse, String originalQuery, String targetFilter) {
    try {
      JsonNode root = objectMapper.readTree(jsonResponse);
      TokenGraph tokenGraph = TokenGraph.builder().originalQuery(originalQuery).build();

      JsonNode detail = root.get(DETAIL);
      if (detail == null) {
        tokenGraph.generatePaths();
        return tokenGraph;
      }

      JsonNode tokenFilters = detail.get(TOKEN_FILTERS);
      if (tokenFilters == null) {
        tokenGraph.generatePaths();
        return tokenGraph;
      }

      tokenFilters.forEach(
          filter -> {
            if (targetFilter.equals(filter.get(NAME).asText())) {
              JsonNode tokens = filter.get(TOKENS);
              if (tokens != null) {
                tokens.forEach(token -> tokenGraph.addToken(createTokenInfo(token)));
              }
            }
          });

      tokenGraph.generatePaths();
      return tokenGraph;
    } catch (IOException e) {
      log.error("ES analyze 응답 파싱 실패: {}", e.getMessage());
      throw new RuntimeException("ES analyze 응답 파싱 실패", e);
    }
  }

  private TokenInfo createTokenInfo(JsonNode token) {
    JsonNode attributes = token.get(ATTRIBUTES);
    int positionLength =
        token.has(POSITION_LENGTH)
            ? token.get(POSITION_LENGTH).asInt()
            : (attributes != null && attributes.has(POSITION_LENGTH)
                ? attributes.get(POSITION_LENGTH).asInt()
                : 1);

    return TokenInfo.builder()
        .token(token.get(TOKEN).asText())
        .type(token.has(TYPE) ? token.get(TYPE).asText() : "word")
        .position(token.get(POSITION).asInt())
        .positionLength(positionLength)
        .startOffset(token.get(START_OFFSET).asInt())
        .endOffset(token.get(END_OFFSET).asInt())
        .build();
  }
}
