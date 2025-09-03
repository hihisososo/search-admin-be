package com.yjlee.search.search.analysis.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenPath {
  private String path;
  private List<String> tokens;
  private boolean isOriginal;

  public int getTokenCount() {
    return tokens != null ? tokens.size() : 0;
  }
}
