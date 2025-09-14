package com.yjlee.search.analysis.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class PositionNode {
  private final int position;
  private final List<TokenInfo> tokens;

  public PositionNode(int position) {
    this.position = position;
    this.tokens = new ArrayList<>();
  }

  public void addToken(TokenInfo token) {
    tokens.add(token);
  }

  public boolean hasTokens() {
    return !tokens.isEmpty();
  }

  public int getTokenCount() {
    return tokens.size();
  }
}
