package com.yjlee.search.search.analysis.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class PositionNode {
  private final int position;
  private final List<TokenGraph.TokenInfo> tokens;

  public PositionNode(int position) {
    this.position = position;
    this.tokens = new ArrayList<>();
  }

  public void addToken(TokenGraph.TokenInfo token) {
    tokens.add(token);
  }

  public boolean hasTokens() {
    return !tokens.isEmpty();
  }

  public int getTokenCount() {
    return tokens.size();
  }
}
