package com.yjlee.search.index.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelExtractor {

  // 하이픈 포함 패턴: 영숫자로 시작하고 하이픈으로 연결된 영숫자 그룹
  private static final Pattern MODEL_PATTERN =
      Pattern.compile("\\b([A-Z0-9]+(?:-[A-Z0-9]+)*)\\b", Pattern.CASE_INSENSITIVE);

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

    Matcher matcher = MODEL_PATTERN.matcher(cleanedName);
    while (matcher.find()) {
      String model = matcher.group(1);
      if (isValidModel(model)) {
        String trimmedModel = model.trim().toLowerCase();

        // 원본 모델명 추가
        if (!models.contains(trimmedModel)) {
          models.add(trimmedModel);
        }

        // 하이픈이 포함된 경우 하이픈 제거 버전도 추가
        if (trimmedModel.contains("-")) {
          String modelWithoutHyphen = trimmedModel.replace("-", "");
          if (!models.contains(modelWithoutHyphen)) {
            models.add(modelWithoutHyphen);
          }
        }
      }
    }

    return models;
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
    String modelWithoutHyphen = model.replace("-", "");

    // 영어만 또는 숫자만으로 구성된 경우 제외
    if (modelWithoutHyphen.matches("^[A-Za-z]+$")) {
      return false; // 영어만
    }
    if (modelWithoutHyphen.matches("^[0-9]+$")) {
      return false; // 숫자만
    }

    // 영어와 숫자가 모두 포함되어야 함
    boolean hasLetter = modelWithoutHyphen.matches(".*[A-Za-z].*");
    boolean hasDigit = modelWithoutHyphen.matches(".*[0-9].*");

    return hasLetter && hasDigit;
  }
}
