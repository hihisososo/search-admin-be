package com.yjlee.search.index.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UnitExtractor {

  // 영어 단위 목록 (하드코딩)
  private static final String EN_UNITS =
      "ml|l|cc|oz|gal|g|kg|mg|lb|ea|pcs|box|pack|set|btl|can|"
          + "cm|mm|m|km|inch|ft|yd|mile|"
          + "gb|mb|kb|tb|gbps|mbps|kbps|bps|hz|khz|mhz|ghz|"
          + "w|kw|mw|v|kv|mv|a|ma|mah|wh|kwh";

  // 한글 단위 목록 (하드코딩)
  private static final String KO_UNITS = "개입|개|장|매|봉|봉지|포|박스|팩|세트|켤레|족|쌍|병|캔|정|알|평|묶음|다발";

  // 일반 단위 패턴 (숫자 + 단위)
  private static final Pattern UNIT_PATTERN_EN =
      Pattern.compile("(\\d+\\.?\\d*)\\s*(" + EN_UNITS + ")(?![a-zA-Z])", Pattern.CASE_INSENSITIVE);

  private static final Pattern UNIT_PATTERN_KO =
      Pattern.compile("(\\d+\\.?\\d*)\\s*(" + KO_UNITS + ")(?![가-힣])");

  // 복합 패턴 (예: 25.4x24.4cm, 1920x1080px)
  private static final Pattern COMPLEX_UNIT_PATTERN =
      Pattern.compile(
          "(\\d+\\.?\\d*)x(\\d+\\.?\\d*)\\s*(" + EN_UNITS + ")(?![a-zA-Z])",
          Pattern.CASE_INSENSITIVE);

  public static List<String> extractUnits(String text) {
    if (text == null || text.isBlank()) {
      return new ArrayList<>();
    }

    Set<String> units = new HashSet<>();

    // 복합 패턴 먼저 처리 (최장 일치 우선)
    Matcher complexMatcher = COMPLEX_UNIT_PATTERN.matcher(text);
    while (complexMatcher.find()) {
      String num1 = complexMatcher.group(1);
      String num2 = complexMatcher.group(2);
      String unit = complexMatcher.group(3).toLowerCase();

      // 원본 복합 형태
      units.add(num1 + "x" + num2 + unit);
      // 각각 분리된 형태도 추가
      units.add(num1 + unit);
      units.add(num2 + unit);
    }

    // 일반 영어 단위 추출
    Matcher enMatcher = UNIT_PATTERN_EN.matcher(text);
    while (enMatcher.find()) {
      String number = enMatcher.group(1);
      String unit = enMatcher.group(2).toLowerCase();
      units.add(number + unit);
    }

    // 한글 단위 추출
    Matcher koMatcher = UNIT_PATTERN_KO.matcher(text);
    while (koMatcher.find()) {
      String number = koMatcher.group(1);
      String unit = koMatcher.group(2);
      units.add(number + unit);
    }

    return new ArrayList<>(units);
  }

  // 검색용 단위 추출 (증강 없이 원본만)
  public static List<String> extractUnitsForSearch(String query) {
    if (query == null || query.isBlank()) {
      return new ArrayList<>();
    }

    Set<String> units = new HashSet<>();

    // 복합 패턴 처리 (원본만)
    Matcher complexMatcher = COMPLEX_UNIT_PATTERN.matcher(query);
    while (complexMatcher.find()) {
      String num1 = complexMatcher.group(1);
      String num2 = complexMatcher.group(2);
      String unit = complexMatcher.group(3).toLowerCase();
      units.add(num1 + "x" + num2 + unit);
    }

    // 일반 영어 단위
    Matcher enMatcher = UNIT_PATTERN_EN.matcher(query);
    while (enMatcher.find()) {
      String number = enMatcher.group(1);
      String unit = enMatcher.group(2).toLowerCase();
      units.add(number + unit);
    }

    // 한글 단위
    Matcher koMatcher = UNIT_PATTERN_KO.matcher(query);
    while (koMatcher.find()) {
      String number = koMatcher.group(1);
      String unit = koMatcher.group(2);
      units.add(number + unit);
    }

    return new ArrayList<>(units);
  }
}
