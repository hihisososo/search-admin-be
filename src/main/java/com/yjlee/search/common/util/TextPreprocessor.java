package com.yjlee.search.common.util;

import java.text.Normalizer;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextPreprocessor {

  private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s\\.]");
  private static final Pattern UNIT_PATTERN =
      Pattern.compile(
          "(\\d+\\.?\\d*)\\s+"
              + "(ml|l|cc|oz|gal|"
              + // 용량 단위
              "개|ea|pcs|장|매|봉|봉지|포|박스|box|팩|pack|"
              + // 수량 단위
              "세트|set|켤레|족|쌍|병|btl|캔|can|정|알|묶음|다발)", // 수량 단위
          Pattern.CASE_INSENSITIVE);

  public static String preprocess(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    return normalizeUnicode(toLowerCase(cleanSpecialChars(text)));
  }

  public static String normalizeUnits(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    return UNIT_PATTERN.matcher(text).replaceAll("$1$2");
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
