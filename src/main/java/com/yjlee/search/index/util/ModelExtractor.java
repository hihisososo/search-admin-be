package com.yjlee.search.index.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModelExtractor {

  private static final Pattern[] MODEL_PATTERNS = {

    // 1. 알파벳 + 숫자 조합 (예: GTX1080, SM-G991N, WH-1000XM4)
    Pattern.compile("([A-Z]{2,}[-]?[A-Z0-9]{3,})", Pattern.CASE_INSENSITIVE),

    // 2. 괄호 안의 모델명 (예: (SM-G991N), [GTX1080])
    Pattern.compile("[\\(\\[]([A-Z0-9-]+)[\\)\\]]"),

    // 3. 일반적인 모델 코드 (3자리 이상 알파벳+숫자)
    Pattern.compile("([A-Z0-9]{3,})", Pattern.CASE_INSENSITIVE)
  };

  public static List<String> extractModels(String productName) {
    List<String> models = new ArrayList<>();

    if (productName == null || productName.isBlank()) {
      return models;
    }

    String cleanedName = productName.trim();

    for (Pattern pattern : MODEL_PATTERNS) {
      Matcher matcher = pattern.matcher(cleanedName);
      while (matcher.find()) {
        String model = extractModelFromMatch(matcher);
        if (isValidModel(model)) {
          String trimmedModel = model.trim();
          if (!models.contains(trimmedModel)) {
            models.add(trimmedModel);
          }
        }
      }
    }

    return models;
  }

  private static String extractModelFromMatch(Matcher matcher) {
    if (matcher.groupCount() >= 1) {
      return matcher.group(1);
    } else {
      return matcher.group();
    }
  }

  private static boolean isValidModel(String model) {
    if (model == null || model.length() < 2) {
      return false;
    }

    // 숫자가 포함되어 있거나, 3자리 이상의 알파벳이면 유효한 모델명으로 판단
    return model.matches(".*[0-9].*") || model.matches("[A-Za-z]{3,}.*");
  }
}
