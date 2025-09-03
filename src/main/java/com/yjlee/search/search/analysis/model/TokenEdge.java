package com.yjlee.search.search.analysis.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenEdge {
  private int fromPosition;
  private int toPosition;
  private String token;
  private String type;
  private int positionLength;
  private int startOffset;
  private int endOffset;

  public boolean isWord() {
    return "word".equals(type);
  }

  public boolean isSynonym() {
    return "SYNONYM".equals(type);
  }

  public boolean isMultiPosition() {
    return positionLength > 1;
  }
}
