package com.yjlee.search.index.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RegDateExtractor {

  public static String extractRegDate(String regDate) {
    if (regDate == null || regDate.isEmpty()) {
      return "";
    }

    return regDate.replace(".", "-").substring(0, 7);
  }
}
