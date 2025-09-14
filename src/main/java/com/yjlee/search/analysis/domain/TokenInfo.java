package com.yjlee.search.analysis.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenInfo {
  private String token;
  private String type;
  private int position;
  private int positionLength;
  private int startOffset;
  private int endOffset;
}
