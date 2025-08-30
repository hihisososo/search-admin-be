package com.yjlee.search.index.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UnitExtractor {

  // JSON에서 로드된 단위 패턴
  private static String EN_UNITS;
  private static String KO_UNITS;

  // 단위 정의 맵 (primary -> UnitDefinition)
  private static final Map<String, UnitDefinition> UNIT_DEFINITIONS = new HashMap<>();

  // 단위 변환 매핑 (모든 단위/동의어 -> 동의어 그룹)
  private static final Map<String, Set<String>> UNIT_MAPPING = new HashMap<>();

  // 단위 정의 모델
  @Data
  private static class UnitDefinition {
    private String primary;
    private List<String> synonyms;
  }

  @Data
  private static class UnitsData {
    private List<UnitDefinition> units;
  }

  static {
    try {
      loadUnitsFromJson();
      buildPatterns();
      initializePatterns();
    } catch (Exception e) {
      log.error("단위 데이터 로드 실패", e);
      // 기본값 설정 (fallback)
      EN_UNITS = "ml|l|cc|oz|gal|g|kg|mg|lb|ea|pcs|box|pack|set|can";
      KO_UNITS = "개|장|매|봉|병|캔|정|알|리터|밀리리터|그램|킬로그램";
      initializePatterns();
    }
  }

  private static void loadUnitsFromJson() throws IOException {
    ObjectMapper mapper = new ObjectMapper();

    try (InputStream is = UnitExtractor.class.getClassLoader().getResourceAsStream("units.json")) {

      if (is == null) {
        throw new IOException("units.json 파일을 찾을 수 없습니다.");
      }

      UnitsData data = mapper.readValue(is, UnitsData.class);

      // 단위 정의 맵 구성
      for (UnitDefinition unit : data.getUnits()) {
        String primary = unit.getPrimary().toLowerCase();
        UNIT_DEFINITIONS.put(primary, unit);

        // UNIT_MAPPING 구성 (동의어 그룹화)
        Set<String> group = new HashSet<>();
        group.add(primary);
        if (unit.getSynonyms() != null) {
          for (String synonym : unit.getSynonyms()) {
            group.add(synonym.toLowerCase());
          }
        }

        // primary와 모든 synonym에 대해 매핑 추가
        UNIT_MAPPING.put(primary, group);
        if (unit.getSynonyms() != null) {
          for (String synonym : unit.getSynonyms()) {
            UNIT_MAPPING.put(synonym.toLowerCase(), group);
          }
        }
      }

      log.info("단위 데이터 로드 완료: {}개 단위", UNIT_DEFINITIONS.size());
    }
  }

  private static void buildPatterns() {
    Set<String> enUnits = new HashSet<>();
    Set<String> koUnits = new HashSet<>();

    // 모든 단위와 동의어를 패턴에 추가
    for (UnitDefinition unit : UNIT_DEFINITIONS.values()) {
      String primary = unit.getPrimary();

      // 한글/영어 자동 판별
      if (isKorean(primary)) {
        koUnits.add(Pattern.quote(primary));
      } else {
        enUnits.add(Pattern.quote(primary));
      }

      // synonyms도 패턴에 추가
      if (unit.getSynonyms() != null) {
        for (String syn : unit.getSynonyms()) {
          if (isKorean(syn)) {
            koUnits.add(Pattern.quote(syn));
          } else {
            enUnits.add(Pattern.quote(syn));
          }
        }
      }
    }

    // 패턴 문자열 생성
    EN_UNITS = String.join("|", enUnits);
    KO_UNITS = String.join("|", koUnits);

    log.debug("영어 단위 패턴: {}", EN_UNITS);
    log.debug("한글 단위 패턴: {}", KO_UNITS);
  }

  private static boolean isKorean(String text) {
    return text != null && text.matches(".*[가-힣]+.*");
  }

  // 일반 단위 패턴 (숫자 + 단위) - 동적으로 생성
  private static Pattern UNIT_PATTERN_EN;
  private static Pattern UNIT_PATTERN_KO;
  private static Pattern COMPLEX_UNIT_PATTERN;

  private static void initializePatterns() {
    if (EN_UNITS != null && !EN_UNITS.isEmpty()) {
      UNIT_PATTERN_EN =
          Pattern.compile(
              "(?<![a-zA-Z])(\\d+\\.?\\d*)\\s*(" + EN_UNITS + ")(?![a-zA-Z])",
              Pattern.CASE_INSENSITIVE);

      COMPLEX_UNIT_PATTERN =
          Pattern.compile(
              "(?<![a-zA-Z])(\\d+\\.?\\d*)x(\\d+\\.?\\d*)\\s*(" + EN_UNITS + ")(?![a-zA-Z])",
              Pattern.CASE_INSENSITIVE);
    }

    if (KO_UNITS != null && !KO_UNITS.isEmpty()) {
      UNIT_PATTERN_KO =
          Pattern.compile("(?<![a-zA-Z])(\\d+\\.?\\d*)\\s*(" + KO_UNITS + ")(?![가-힣])");
    }
  }

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

  // 색인용 단위 추출 및 증강 (모든 가능한 표현 생성)
  public static List<String> extractUnitsForIndexing(String text) {
    if (text == null || text.isBlank()) {
      return new ArrayList<>();
    }

    Set<String> augmentedUnits = new HashSet<>();

    // 복합 패턴 먼저 처리
    Matcher complexMatcher = COMPLEX_UNIT_PATTERN.matcher(text);
    while (complexMatcher.find()) {
      String num1 = complexMatcher.group(1);
      String num2 = complexMatcher.group(2);
      String unit = complexMatcher.group(3).toLowerCase();

      // 원본 복합 형태 증강
      augmentedUnits.add(num1 + "x" + num2 + unit);

      // 각각 분리된 형태도 증강
      augmentedUnits.addAll(augmentUnit(num1, unit));
      augmentedUnits.addAll(augmentUnit(num2, unit));
    }

    // 일반 영어 단위 추출 및 증강
    Matcher enMatcher = UNIT_PATTERN_EN.matcher(text);
    while (enMatcher.find()) {
      String number = enMatcher.group(1);
      String unit = enMatcher.group(2).toLowerCase();
      augmentedUnits.addAll(augmentUnit(number, unit));
    }

    // 한글 단위 추출 및 증강
    Matcher koMatcher = UNIT_PATTERN_KO.matcher(text);
    while (koMatcher.find()) {
      String number = koMatcher.group(1);
      String unit = koMatcher.group(2);
      augmentedUnits.addAll(augmentUnit(number, unit));
    }

    return new ArrayList<>(augmentedUnits);
  }

  // 단일 단위를 증강하여 모든 가능한 표현 반환
  private static Set<String> augmentUnit(String numberStr, String unit) {
    Set<String> augmented = new HashSet<>();
    String unitLower = unit.toLowerCase();

    // 원본 추가
    augmented.add(numberStr + unitLower);

    // 동의어 단위들 추가
    Set<String> synonyms = UNIT_MAPPING.get(unitLower);
    if (synonyms != null) {
      for (String synonym : synonyms) {
        augmented.add(numberStr + synonym);
      }
    }

    // 숫자 변환 처리 제거 - 원본만 유지

    return augmented;
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

  // 하나의 단위를 동의어로 확장 (검색용)
  public static Set<String> expandUnitSynonyms(String unit) {
    if (unit == null || unit.isBlank()) {
      return new HashSet<>();
    }

    Set<String> expanded = new HashSet<>();

    // 숫자와 단위 분리를 위한 패턴
    Pattern numericUnitPattern = Pattern.compile("^(\\d+\\.?\\d*)(.+)$");
    Matcher matcher = numericUnitPattern.matcher(unit);

    if (matcher.matches()) {
      String number = matcher.group(1);
      String unitPart = matcher.group(2).toLowerCase();

      // 동의어 그룹 찾기
      Set<String> synonymGroup = UNIT_MAPPING.get(unitPart);

      if (synonymGroup != null && !synonymGroup.isEmpty()) {
        // 숫자 + 각 동의어 조합 (자기 자신 포함)
        for (String synonym : synonymGroup) {
          expanded.add(number + synonym);
        }
      } else {
        // 매핑이 없으면 원본만 반환
        expanded.add(unit);
      }
    } else {
      // 숫자가 없는 단위는 원본만 반환
      expanded.add(unit);
    }

    return expanded;
  }
}
