package com.yjlee.search.recommendation.util;

import org.springframework.stereotype.Component;

@Component
public class KeywordNormalizer {

  public String normalize(String keyword) {
    if (keyword == null || keyword.isEmpty()) {
      return "";
    }

    return keyword.replaceAll("\\s+", "").toLowerCase();
  }

  public boolean isSameKeyword(String keyword1, String keyword2) {
    return normalize(keyword1).equals(normalize(keyword2));
  }
}
