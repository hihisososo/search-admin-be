package com.yjlee.search.index.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
  private static final String KO_UNITS =
      "개입|개|장|매|봉|봉지|포|박스|팩|세트|켤레|족|쌍|병|캔|정|알|평|묶음|다발|인치|리터|밀리리터|그램|킬로그램|밀리그램|미터|센티미터|밀리미터|킬로미터|킬로바이트|메가바이트|기가바이트|테라바이트|와트|킬로와트|볼트|암페어|헤르츠";

  // 단위 변환 매핑 (같은 의미의 단위들을 그룹화)
  private static final Map<String, Set<String>> UNIT_MAPPING = new HashMap<>();

  static {
    // 용량 단위
    addUnitGroup("ml", "ml", "밀리리터", "밀리", "cc", "시시");
    addUnitGroup("l", "l", "리터", "ℓ");
    addUnitGroup("oz", "oz", "온스");
    addUnitGroup("gal", "gal", "갤런", "갤론");

    // 무게 단위
    addUnitGroup("mg", "mg", "밀리그램");
    addUnitGroup("g", "g", "그램", "그람");
    addUnitGroup("kg", "kg", "킬로그램", "킬로", "키로");
    addUnitGroup("lb", "lb", "파운드");

    // 길이 단위
    addUnitGroup("mm", "mm", "밀리미터", "밀리");
    addUnitGroup("cm", "cm", "센티미터", "센티", "센치");
    addUnitGroup("m", "m", "미터");
    addUnitGroup("km", "km", "킬로미터", "키로미터");
    addUnitGroup("inch", "inch", "인치", "in", "\"");
    addUnitGroup("ft", "ft", "피트", "feet");
    addUnitGroup("yd", "yd", "야드");
    addUnitGroup("mile", "mile", "마일");

    // 개수/포장 단위
    addUnitGroup("ea", "ea", "개");
    addUnitGroup("개입", "개입");
    addUnitGroup("장", "장", "매");
    addUnitGroup("box", "box", "박스", "boxes");
    addUnitGroup("pack", "pack", "팩", "packs");
    addUnitGroup("set", "set", "세트", "셋트", "sets");
    addUnitGroup("btl", "btl", "병", "bottle", "bottles");
    addUnitGroup("can", "can", "캔", "cans");
    addUnitGroup("정", "정", "알");
    addUnitGroup("봉", "봉", "봉지", "포");
    addUnitGroup("켤레", "켤레", "족", "쌍");
    addUnitGroup("묶음", "묶음", "다발");

    // 데이터 용량 단위
    addUnitGroup("kb", "kb", "킬로바이트");
    addUnitGroup("mb", "mb", "메가바이트");
    addUnitGroup("gb", "gb", "기가바이트");
    addUnitGroup("tb", "tb", "테라바이트");

    // 전기/전력 단위
    addUnitGroup("w", "w", "와트");
    addUnitGroup("kw", "kw", "킬로와트");
    addUnitGroup("v", "v", "볼트");
    addUnitGroup("a", "a", "암페어");
    addUnitGroup("ma", "ma", "밀리암페어");
    addUnitGroup("mah", "mah", "밀리암페어시");
    addUnitGroup("wh", "wh", "와트시");
    addUnitGroup("kwh", "kwh", "킬로와트시");

    // 주파수 단위
    addUnitGroup("hz", "hz", "헤르츠");
    addUnitGroup("khz", "khz", "킬로헤르츠");
    addUnitGroup("mhz", "mhz", "메가헤르츠");
    addUnitGroup("ghz", "ghz", "기가헤르츠");
  }

  private static void addUnitGroup(String key, String... units) {
    Set<String> group = new HashSet<>();
    for (String unit : units) {
      group.add(unit.toLowerCase());
    }
    for (String unit : units) {
      UNIT_MAPPING.put(unit.toLowerCase(), group);
    }
  }

  // 단위 변환 관계 정의 (예: 1L = 1000ml)
  private static final Map<String, Double> UNIT_CONVERSIONS = new HashMap<>();

  static {
    // 용량 변환
    UNIT_CONVERSIONS.put("l_to_ml", 1000.0);
    UNIT_CONVERSIONS.put("ml_to_l", 0.001);
    UNIT_CONVERSIONS.put("gal_to_l", 3.78541);
    UNIT_CONVERSIONS.put("oz_to_ml", 29.5735);

    // 무게 변환
    UNIT_CONVERSIONS.put("kg_to_g", 1000.0);
    UNIT_CONVERSIONS.put("g_to_kg", 0.001);
    UNIT_CONVERSIONS.put("g_to_mg", 1000.0);
    UNIT_CONVERSIONS.put("mg_to_g", 0.001);
    UNIT_CONVERSIONS.put("lb_to_kg", 0.453592);
    UNIT_CONVERSIONS.put("kg_to_lb", 2.20462);

    // 길이 변환
    UNIT_CONVERSIONS.put("m_to_cm", 100.0);
    UNIT_CONVERSIONS.put("cm_to_m", 0.01);
    UNIT_CONVERSIONS.put("cm_to_mm", 10.0);
    UNIT_CONVERSIONS.put("mm_to_cm", 0.1);
    UNIT_CONVERSIONS.put("km_to_m", 1000.0);
    UNIT_CONVERSIONS.put("m_to_km", 0.001);
    UNIT_CONVERSIONS.put("inch_to_cm", 2.54);
    UNIT_CONVERSIONS.put("cm_to_inch", 0.393701);
    UNIT_CONVERSIONS.put("ft_to_m", 0.3048);
    UNIT_CONVERSIONS.put("m_to_ft", 3.28084);

    // 데이터 용량 변환
    UNIT_CONVERSIONS.put("gb_to_mb", 1024.0);
    UNIT_CONVERSIONS.put("mb_to_gb", 1.0 / 1024.0);
    UNIT_CONVERSIONS.put("mb_to_kb", 1024.0);
    UNIT_CONVERSIONS.put("kb_to_mb", 1.0 / 1024.0);
    UNIT_CONVERSIONS.put("tb_to_gb", 1024.0);
    UNIT_CONVERSIONS.put("gb_to_tb", 1.0 / 1024.0);
  }

  // 일반 단위 패턴 (숫자 + 단위)
  private static final Pattern UNIT_PATTERN_EN =
      Pattern.compile(
          "(?<![a-zA-Z])(\\d+\\.?\\d*)\\s*(" + EN_UNITS + ")(?![a-zA-Z])",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern UNIT_PATTERN_KO =
      Pattern.compile("(?<![a-zA-Z])(\\d+\\.?\\d*)\\s*(" + KO_UNITS + ")(?![가-힣])");

  // 복합 패턴 (예: 25.4x24.4cm, 1920x1080px)
  private static final Pattern COMPLEX_UNIT_PATTERN =
      Pattern.compile(
          "(?<![a-zA-Z])(\\d+\\.?\\d*)x(\\d+\\.?\\d*)\\s*(" + EN_UNITS + ")(?![a-zA-Z])",
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

    // 숫자 변환 처리
    try {
      double number = Double.parseDouble(numberStr);

      // 단위 변환 적용
      augmented.addAll(convertUnits(number, unitLower));

    } catch (NumberFormatException e) {
      // 숫자 파싱 실패 시 원본만 유지
    }

    return augmented;
  }

  // 단위 변환을 통한 추가 표현 생성
  private static Set<String> convertUnits(double value, String unit) {
    Set<String> converted = new HashSet<>();
    String unitLower = unit.toLowerCase();

    // L <-> ml 변환
    if (unitLower.equals("l") || unitLower.equals("리터")) {
      double mlValue = value * 1000;
      converted.add(formatNumber(mlValue) + "ml");
      converted.add(formatNumber(mlValue) + "밀리리터");
      converted.add(formatNumber(mlValue) + "밀리");
      converted.add(formatNumber(mlValue) + "cc");
      converted.add(formatNumber(mlValue) + "시시");
    } else if (unitLower.equals("ml") || unitLower.equals("밀리리터") || unitLower.equals("cc")) {
      double lValue = value / 1000;
      if (lValue >= 0.1) { // 0.1L 이상만 L로 표시
        converted.add(formatNumber(lValue) + "l");
        converted.add(formatNumber(lValue) + "리터");
        converted.add(formatNumber(lValue) + "ℓ");
      }
    }

    // kg <-> g 변환
    if (unitLower.equals("kg") || unitLower.equals("킬로그램") || unitLower.equals("키로")) {
      double gValue = value * 1000;
      converted.add(formatNumber(gValue) + "g");
      converted.add(formatNumber(gValue) + "그램");
      converted.add(formatNumber(gValue) + "그람");
    } else if (unitLower.equals("g") || unitLower.equals("그램") || unitLower.equals("그람")) {
      double kgValue = value / 1000;
      if (kgValue >= 0.1) { // 0.1kg 이상만 kg로 표시
        converted.add(formatNumber(kgValue) + "kg");
        converted.add(formatNumber(kgValue) + "킬로그램");
        converted.add(formatNumber(kgValue) + "킬로");
        converted.add(formatNumber(kgValue) + "키로");
      }
      // mg 변환
      if (value >= 100) { // 100g 이상은 mg로 변환하지 않음
        // skip mg conversion for large values
      } else {
        double mgValue = value * 1000;
        converted.add(formatNumber(mgValue) + "mg");
        converted.add(formatNumber(mgValue) + "밀리그램");
      }
    } else if (unitLower.equals("mg") || unitLower.equals("밀리그램")) {
      double gValue = value / 1000;
      if (gValue >= 0.1) { // 0.1g 이상만 g로 표시
        converted.add(formatNumber(gValue) + "g");
        converted.add(formatNumber(gValue) + "그램");
        converted.add(formatNumber(gValue) + "그람");
      }
    }

    // m <-> cm <-> mm 변환
    if (unitLower.equals("m") || unitLower.equals("미터")) {
      double cmValue = value * 100;
      converted.add(formatNumber(cmValue) + "cm");
      converted.add(formatNumber(cmValue) + "센티미터");
      converted.add(formatNumber(cmValue) + "센티");
      converted.add(formatNumber(cmValue) + "센치");

      double mmValue = value * 1000;
      if (mmValue <= 10000) { // 10000mm 이하만 mm로 표시
        converted.add(formatNumber(mmValue) + "mm");
        converted.add(formatNumber(mmValue) + "밀리미터");
        converted.add(formatNumber(mmValue) + "밀리");
      }
    } else if (unitLower.equals("cm")
        || unitLower.equals("센티미터")
        || unitLower.equals("센티")
        || unitLower.equals("센치")) {
      double mValue = value / 100;
      if (mValue >= 0.1) { // 0.1m 이상만 m로 표시
        converted.add(formatNumber(mValue) + "m");
        converted.add(formatNumber(mValue) + "미터");
      }

      double mmValue = value * 10;
      converted.add(formatNumber(mmValue) + "mm");
      converted.add(formatNumber(mmValue) + "밀리미터");
      converted.add(formatNumber(mmValue) + "밀리");
    } else if (unitLower.equals("mm") || unitLower.equals("밀리미터")) {
      double cmValue = value / 10;
      if (cmValue >= 1) { // 1cm 이상만 cm로 표시
        converted.add(formatNumber(cmValue) + "cm");
        converted.add(formatNumber(cmValue) + "센티미터");
        converted.add(formatNumber(cmValue) + "센티");
        converted.add(formatNumber(cmValue) + "센치");
      }

      double mValue = value / 1000;
      if (mValue >= 0.1) { // 0.1m 이상만 m로 표시
        converted.add(formatNumber(mValue) + "m");
        converted.add(formatNumber(mValue) + "미터");
      }
    }

    // GB <-> MB <-> KB 변환
    if (unitLower.equals("gb") || unitLower.equals("기가바이트")) {
      double mbValue = value * 1024;
      converted.add(formatNumber(mbValue) + "mb");
      converted.add(formatNumber(mbValue) + "메가바이트");
    } else if (unitLower.equals("mb") || unitLower.equals("메가바이트")) {
      double gbValue = value / 1024;
      if (gbValue >= 0.5) { // 0.5GB 이상만 GB로 표시
        converted.add(formatNumber(gbValue) + "gb");
        converted.add(formatNumber(gbValue) + "기가바이트");
      }

      double kbValue = value * 1024;
      if (kbValue <= 100000) { // 100000KB 이하만 KB로 표시
        converted.add(formatNumber(kbValue) + "kb");
        converted.add(formatNumber(kbValue) + "킬로바이트");
      }
    } else if (unitLower.equals("kb") || unitLower.equals("킬로바이트")) {
      double mbValue = value / 1024;
      if (mbValue >= 0.5) { // 0.5MB 이상만 MB로 표시
        converted.add(formatNumber(mbValue) + "mb");
        converted.add(formatNumber(mbValue) + "메가바이트");
      }
    } else if (unitLower.equals("tb") || unitLower.equals("테라바이트")) {
      double gbValue = value * 1024;
      converted.add(formatNumber(gbValue) + "gb");
      converted.add(formatNumber(gbValue) + "기가바이트");
    }

    // inch <-> cm 변환
    if (unitLower.equals("inch")
        || unitLower.equals("인치")
        || unitLower.equals("in")
        || unitLower.equals("\"")) {
      double cmValue = value * 2.54;
      converted.add(formatNumber(cmValue) + "cm");
      converted.add(formatNumber(cmValue) + "센티미터");
      converted.add(formatNumber(cmValue) + "센티");
      converted.add(formatNumber(cmValue) + "센치");
    }

    return converted;
  }

  // 숫자를 적절한 형식으로 포맷팅 (소수점 처리)
  private static String formatNumber(double value) {
    if (value == (long) value) {
      return String.valueOf((long) value);
    } else {
      // 소수점 둘째자리까지만 표시하고 불필요한 0 제거
      String formatted = String.format("%.2f", value);
      formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
      return formatted;
    }
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
