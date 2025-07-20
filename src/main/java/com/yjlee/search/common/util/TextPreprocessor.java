package com.yjlee.search.common.util;

import java.text.Normalizer;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextPreprocessor {

  private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s]");

  public static String preprocess(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    return normalizeUnicode(toLowerCase(cleanSpecialChars(text)));
  }

  private static String cleanSpecialChars(String text) {
    return SPECIAL_CHARS_PATTERN
        .matcher(text.trim())
        .replaceAll(" ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static String toLowerCase(String text) {
    return text.toLowerCase();
  }

  private static String normalizeUnicode(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFC);
  }
}
