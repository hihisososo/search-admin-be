package com.yjlee.search.index.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelExtractor {

  // 모델명 패턴:
  // 1. 하이픈으로 연결된 영숫자 그룹 (점 제외)
  // 2. 영숫자 혼합 3자리 이상 연속 문자열
  private static final Pattern MODEL_PATTERN_HYPHEN =
      Pattern.compile("(?<![A-Z0-9])([A-Z0-9]+(?:-[A-Z0-9]+)+)(?![A-Z0-9])", Pattern.CASE_INSENSITIVE);

  private static final Pattern MODEL_PATTERN_MIXED =
      Pattern.compile("(?<![A-Z0-9])((?:[A-Z]+[0-9]+|[0-9]+[A-Z]+)[A-Z0-9]*)(?![A-Z0-9])", Pattern.CASE_INSENSITIVE);

  // 단위 패턴 (제외할 것들)
  private static final Pattern UNIT_PATTERN =
      Pattern.compile(
          "^\\d+\\.?\\d*(ml|l|cc|oz|gal|g|kg|mg|ea|pcs|box|pack|set|btl|can|개|입|장|매|봉|팩|세트|병|캔|정)$",
          Pattern.CASE_INSENSITIVE);

  public static List<String> extractModels(String productName) {
    List<String> models = new ArrayList<>();

    if (productName == null || productName.isBlank()) {
      return models;
    }

    String cleanedName = productName.trim();

    // 하이픈 패턴 매칭
    Matcher hyphenMatcher = MODEL_PATTERN_HYPHEN.matcher(cleanedName);
    while (hyphenMatcher.find()) {
      String model = hyphenMatcher.group(1);
      if (isValidModel(model)) {
        String trimmedModel = model.trim().toLowerCase();

        // 원본 모델명 추가
        if (!models.contains(trimmedModel)) {
          models.add(trimmedModel);
        }

        // 하이픈 제거한 버전 추가
        String modelWithoutSeparators = trimmedModel.replace("-", "");
        if (!models.contains(modelWithoutSeparators)) {
          models.add(modelWithoutSeparators);
        }

        // 하이픈으로 분리된 각 토큰도 추가
        String[] tokens = trimmedModel.split("-");
        for (String token : tokens) {
          if (!token.isEmpty() && !models.contains(token)) {
            models.add(token);
          }
        }
      }
    }

    // 혼합 패턴 매칭
    Matcher mixedMatcher = MODEL_PATTERN_MIXED.matcher(cleanedName);
    while (mixedMatcher.find()) {
      String model = mixedMatcher.group(1);
      if (isValidModel(model)) {
        String trimmedModel = model.trim().toLowerCase();
        if (!models.contains(trimmedModel)) {
          models.add(trimmedModel);
        }
      }
    }

    return models;
  }

  // 검색용 모델 추출 (확장 없이 원본만 반환)
  public static List<String> extractModelsForSearch(String query) {
    List<String> models = new ArrayList<>();

    if (query == null || query.isBlank()) {
      return models;
    }

    String cleanedQuery = query.trim();

    // 하이픈 패턴 매칭
    Matcher hyphenMatcher = MODEL_PATTERN_HYPHEN.matcher(cleanedQuery);
    while (hyphenMatcher.find()) {
      String model = hyphenMatcher.group(1);
      if (isValidModel(model)) {
        String trimmedModel = model.trim().toLowerCase();
        if (!models.contains(trimmedModel)) {
          models.add(trimmedModel);
        }
      }
    }

    // 혼합 패턴 매칭
    Matcher mixedMatcher = MODEL_PATTERN_MIXED.matcher(cleanedQuery);
    while (mixedMatcher.find()) {
      String model = mixedMatcher.group(1);
      if (isValidModel(model)) {
        String trimmedModel = model.trim().toLowerCase();
        if (!models.contains(trimmedModel)) {
          models.add(trimmedModel);
        }
      }
    }

    return models;
  }

  // 단위를 제외한 후 모델 추출 (검색용)
  public static List<String> extractModelsExcludingUnits(String query, List<String> units) {
    if (query == null || query.isBlank()) {
      return new ArrayList<>();
    }

    String processedQuery = query;

    // 단위 부분을 공백으로 치환하여 제거
    if (units != null && !units.isEmpty()) {
      for (String unit : units) {
        processedQuery = processedQuery.replaceAll("\\b" + Pattern.quote(unit) + "\\b", " ");
      }
    }

    // 단위가 제거된 텍스트에서 모델명 추출
    return extractModelsForSearch(processedQuery);
  }

  private static boolean isValidModel(String model) {
    if (model == null || model.length() < 3) {
      return false;
    }

    // 단위 패턴과 매칭되면 제외
    if (UNIT_PATTERN.matcher(model).matches()) {
      return false;
    }

    // 하이픈 제거한 버전으로 검증
    String modelWithoutSeparators = model.replace("-", "");

    // 영어만 또는 숫자만으로 구성된 경우 제외
    if (modelWithoutSeparators.matches("^[A-Za-z]+$")) {
      return false; // 영어만
    }
    if (modelWithoutSeparators.matches("^[0-9]+$")) {
      return false; // 숫자만
    }

    // 영어와 숫자가 모두 포함되어야 함
    boolean hasLetter = modelWithoutSeparators.matches(".*[A-Za-z].*");
    boolean hasDigit = modelWithoutSeparators.matches(".*[0-9].*");

    return hasLetter && hasDigit;
  }
}
