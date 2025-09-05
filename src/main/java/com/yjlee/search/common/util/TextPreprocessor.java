package com.yjlee.search.common.util;

import static java.text.Normalizer.Form;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextPreprocessor {

  // 의미있는 특수문자를 보존: /, -, +, &
  private static final Pattern SPECIAL_CHARS_PATTERN =
      Pattern.compile("[^\\p{L}\\p{N}\\s\\.\\-/+&]");

  // 천단위 구분자 패턴
  private static final Pattern THOUSAND_SEPARATOR_PATTERN =
      Pattern.compile("(\\d),(?=\\d{3}(?:[,.]|$))");

  // 영어 단위 패턴 - 확장된 단위 목록
  private static final Pattern UNIT_PATTERN_EN =
      Pattern.compile(
          "(\\d+\\.?\\d*)\\s+(ml|l|cc|oz|gal|g|kg|mg|mm|cm|m|inch|ea|pcs|box|pack|set|btl|can)(?![a-zA-Z0-9])",
          Pattern.CASE_INSENSITIVE);

  // 한글 단위 패턴 - 확장된 단위 목록
  private static final Pattern UNIT_PATTERN_KO =
      Pattern.compile(
          "(\\d+\\.?\\d*)\\s+(그램|킬로그램|킬로|밀리그램|밀리리터|밀리|리터|밀리미터|센티미터|센치|미터|meter|인치|"
              + "개입|개|장|매|봉지|봉|포|박스|팩|세트|켤레|족|쌍|병|캔|정|알|평|묶음|다발|단|시간|분)(?![가-힣a-zA-Z0-9])");

  // 연속 단위 분해 패턴
  private static final Pattern CONSECUTIVE_UNITS_PATTERN =
      Pattern.compile(
          "(\\d+\\.?\\d*"
              + "(?:ml|l|cc|oz|gal|g|kg|mg|mm|cm|m|inch|ea|pcs|box|pack|set|"
              + "그램|킬로그램|킬로|밀리그램|밀리리터|밀리|리터|밀리미터|센티미터|센치|미터|인치|"
              + "개입|개|장|매|봉지|봉|포|박스|팩|세트|켤레|족|쌍|병|캔|정|알|평|묶음|다발|단|시간|분))"
              + "(?=\\d)",
          Pattern.CASE_INSENSITIVE);

  // 크기 표시 분해 패턴 (3차원)
  private static final Pattern SIZE_PATTERN_3D =
      Pattern.compile(
          "(\\d+\\.?\\d*)x(\\d+\\.?\\d*)x(\\d+\\.?\\d*)"
              + "((?:ml|l|cc|oz|gal|g|kg|mg|mm|cm|m|inch|"
              + "그램|킬로그램|킬로|밀리그램|밀리리터|밀리|리터|밀리미터|센티미터|센치|미터|인치)?)",
          Pattern.CASE_INSENSITIVE);

  // 크기 표시 분해 패턴 (2차원)
  private static final Pattern SIZE_PATTERN_2D =
      Pattern.compile(
          "(\\d+\\.?\\d*)x(\\d+\\.?\\d*)"
              + "((?:ml|l|cc|oz|gal|g|kg|mg|mm|cm|m|inch|"
              + "그램|킬로그램|킬로|밀리그램|밀리리터|밀리|리터|밀리미터|센티미터|센치|미터|인치)?)",
          Pattern.CASE_INSENSITIVE);

  public static String preprocess(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    // 1. 천단위 구분자 제거
    text = removeThousandSeparators(text);

    // 2. 크기 표시 분해 (x를 먼저 처리)
    text = expandSizeNotation(text);

    // 3. 단위 정규화 (공백 제거)
    text = normalizeUnits(text);

    // 4. 연속 단위 분해
    text = separateConsecutiveUnits(text);

    // 5. 기존 전처리 (특수문자 제거, 소문자 변환, 유니코드 정규화)
    return normalizeUnicode(toLowerCase(cleanSpecialChars(text)));
  }

  public static String normalizeUnits(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    // 영어 단위 정규화 (공백 제거) - 단위 뒤에 영/숫자가 없을 때만
    text = UNIT_PATTERN_EN.matcher(text).replaceAll("$1$2");
    // 한글 단위 정규화 (공백 제거) - 단위 뒤에 한글/영/숫자가 없을 때만
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
    // 특수문자 제거 후 연속된 공백 정리
    String cleaned =
        SPECIAL_CHARS_PATTERN.matcher(text.trim()).replaceAll(" ").replaceAll("\\s+", " ").trim();

    return cleaned;
  }

  private static String toLowerCase(String text) {
    return text.toLowerCase();
  }

  private static String normalizeUnicode(String text) {
    return Normalizer.normalize(text, Form.NFC);
  }

  private static String removeThousandSeparators(String text) {
    // 여러 번 적용해서 모든 콤마 제거
    String result = text;
    String prev;
    do {
      prev = result;
      result = THOUSAND_SEPARATOR_PATTERN.matcher(result).replaceAll("$1");
    } while (!result.equals(prev));
    return result;
  }

  private static String expandSizeNotation(String text) {
    // 3차원 크기 표시 분해
    text = SIZE_PATTERN_3D.matcher(text).replaceAll("$1 x $2 x $3$4");
    // 2차원 크기 표시 분해
    text = SIZE_PATTERN_2D.matcher(text).replaceAll("$1 x $2$3");
    return text;
  }

  private static String separateConsecutiveUnits(String text) {
    // 연속된 단위를 공백으로 분리
    return CONSECUTIVE_UNITS_PATTERN.matcher(text).replaceAll("$1 ");
  }
}
