package com.yjlee.search.evaluation.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RelevanceStatus {
  UNSPECIFIED("미지정", "UNSPECIFIED"),
  RELEVANT("정답", "RELEVANT"),
  IRRELEVANT("오답", "IRRELEVANT");

  private final String displayName;
  private final String code;

  public static RelevanceStatus fromBoolean(Boolean isRelevant) {
    if (isRelevant == null) {
      return UNSPECIFIED;
    }
    return isRelevant ? RELEVANT : IRRELEVANT;
  }

  public Boolean toBoolean() {
    return switch (this) {
      case RELEVANT -> true;
      case IRRELEVANT -> false;
      case UNSPECIFIED -> null;
    };
  }
}
