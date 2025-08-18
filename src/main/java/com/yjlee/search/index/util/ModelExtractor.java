package com.yjlee.search.index.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelExtractor {

  // 단순 패턴: 3자리 이상 영숫자
  private static final Pattern MODEL_PATTERN =
      Pattern.compile("\\b([A-Z0-9]{3,})\\b", Pattern.CASE_INSENSITIVE);

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
        if (!models.contains(trimmedModel)) {
          models.add(trimmedModel);
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

    // 숫자가 반드시 포함되어야 함
    return model.matches(".*[0-9].*");
  }
}
