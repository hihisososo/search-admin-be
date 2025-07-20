package com.yjlee.search.index.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BrandExtractor {

  public static String extractBrand(String productName) {
    if (productName == null || productName.isBlank()) {
      return "";
    }

    String[] words = productName.trim().split("\\s+");

    if (words.length == 0 || words[0].isBlank()) {
      return "";
    }

    return words[0].trim();
  }
}
