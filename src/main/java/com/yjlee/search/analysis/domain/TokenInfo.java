package com.yjlee.search.analysis.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenInfo {
  private static final String WORD = "word";
  private static final String SYNONYM = "SYNONYM";
  private static final String ADDITIONAL = "additional";

  private String token;
  private String type;
  private int position;
  private int positionLength;
  private int startOffset;
  private int endOffset;

  public int getEndPosition() {
    return position + positionLength;
  }

  public boolean isWord() {
    return WORD.equals(type);
  }

  public boolean isSynonym() {
    return SYNONYM.equals(type);
  }

  public boolean isAdditional() {
    return ADDITIONAL.equals(type);
  }

  public boolean isMultiPosition() {
    return positionLength > 1;
  }
}
