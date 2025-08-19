package com.yjlee.search.common.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextPreprocessor {

  private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s\\.]");

  // 영어 단위 패턴 - 공백 포함/미포함 모두 처리
  private static final Pattern UNIT_PATTERN_EN =
      Pattern.compile(
          "(\\d+\\.?\\d*)\\s*(ml|l|cc|oz|gal|g|kg|mg|ea|pcs|box|pack|set|btl|can)(?![a-zA-Z])",
          Pattern.CASE_INSENSITIVE);

  // 한글 단위 패턴 - 공백 포함/미포함 모두 처리
  private static final Pattern UNIT_PATTERN_KO =
      Pattern.compile("(\\d+\\.?\\d*)\\s*(개입|개|장|매|봉|봉지|포|박스|팩|세트|켤레|족|쌍|병|캔|정|알|평|묶음|다발)");

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

    // 영어 단위 정규화 (공백 제거)
    text = UNIT_PATTERN_EN.matcher(text).replaceAll("$1$2");
    // 한글 단위 정규화 (공백 제거)
    text = UNIT_PATTERN_KO.matcher(text).replaceAll("$1$2");
    
    return text;
  }

  public static String extractUnits(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    List<String> units = new ArrayList<>();

    // 영어 단위 추출
    Matcher matcherEn = UNIT_PATTERN_EN.matcher(text);
    while (matcherEn.find()) {
      String number = matcherEn.group(1);
      String unit = matcherEn.group(2);
      units.add((number + unit).toLowerCase());
    }

    // 한글 단위 추출
    Matcher matcherKo = UNIT_PATTERN_KO.matcher(text);
    while (matcherKo.find()) {
      String number = matcherKo.group(1);
      String unit = matcherKo.group(2);
      units.add((number + unit).toLowerCase());
    }

    return String.join(" ", units);
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
