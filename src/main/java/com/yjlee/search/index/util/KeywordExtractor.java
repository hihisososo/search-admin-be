package com.yjlee.search.index.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KeywordExtractor {

  // 색상 관련 키워드
  private static final Set<String> COLORS =
      Set.of(
          "빨강", "빨간", "레드", "red", "파랑", "파란", "블루", "blue", "초록", "녹색", "그린", "green", "검정", "검은",
          "블랙", "black", "하양", "흰", "화이트", "white", "회색", "그레이", "gray", "grey", "노랑", "노란", "옐로우",
          "yellow", "보라", "퍼플", "purple", "분홍", "핑크", "pink", "금색", "골드", "gold", "은색", "실버",
          "silver", "로즈골드", "rose", "베이지", "beige");

  // 용량/크기 관련 패턴
  private static final Pattern[] SPEC_PATTERNS = {
    Pattern.compile("(\\d+\\s*(?:GB|TB|MB|인치|mm|cm))", Pattern.CASE_INSENSITIVE),
    Pattern.compile("(\\d+\\.\\d+\\s*(?:인치|mm|cm))", Pattern.CASE_INSENSITIVE),
    Pattern.compile("(\\d+\\s*(?:코어|메가픽셀|MP))", Pattern.CASE_INSENSITIVE)
  };

  // 특징 키워드
  private static final Set<String> FEATURES =
      Set.of(
          "프로",
          "pro",
          "플러스",
          "plus",
          "맥스",
          "max",
          "미니",
          "mini",
          "울트라",
          "ultra",
          "에어",
          "air",
          "라이트",
          "lite",
          "스탠다드",
          "standard",
          "프리미엄",
          "premium",
          "디럭스",
          "deluxe",
          "베이직",
          "basic",
          "게이밍",
          "gaming",
          "워치",
          "watch",
          "북",
          "book",
          "패드",
          "pad");

  public static List<String> extractColors(String productName) {
    if (productName == null || productName.isBlank()) {
      return new ArrayList<>();
    }

    String lowerName = productName.toLowerCase();
    return COLORS.stream()
        .filter(color -> lowerName.contains(color.toLowerCase()))
        .collect(Collectors.toList());
  }

  public static List<String> extractSpecifications(String productName) {
    List<String> specs = new ArrayList<>();

    if (productName == null || productName.isBlank()) {
      return specs;
    }

    for (Pattern pattern : SPEC_PATTERNS) {
      Matcher matcher = pattern.matcher(productName);
      while (matcher.find()) {
        String spec = matcher.group(1).trim();
        if (!specs.contains(spec)) {
          specs.add(spec);
          // 숫자만 별도로도 추가 (예: "128GB" -> "128"도 추가)
          String numOnly = spec.replaceAll("[^0-9.]", "");
          if (!numOnly.isEmpty() && !specs.contains(numOnly)) {
            specs.add(numOnly);
          }
        }
      }
    }

    return specs;
  }

  public static List<String> extractFeatures(String productName) {
    if (productName == null || productName.isBlank()) {
      return new ArrayList<>();
    }

    String lowerName = productName.toLowerCase();
    return FEATURES.stream()
        .filter(feature -> lowerName.contains(feature.toLowerCase()))
        .collect(Collectors.toList());
  }

  public static List<String> extractNgrams(String productName, int minLength, int maxLength) {
    List<String> ngrams = new ArrayList<>();

    if (productName == null || productName.isBlank()) {
      return ngrams;
    }

    String[] words = productName.trim().split("\\s+");

    // 1-gram to n-gram
    for (int i = 0; i < words.length; i++) {
      for (int j = i + 1; j <= Math.min(i + maxLength, words.length); j++) {
        if (j - i >= minLength) {
          String ngram = String.join(" ", Arrays.copyOfRange(words, i, j));
          if (ngram.length() >= 2 && !ngrams.contains(ngram)) { // 최소 2글자 이상
            ngrams.add(ngram);
          }
        }
      }
    }

    return ngrams;
  }

  public static List<String> extractAllKeywords(String productName) {
    Set<String> allKeywords = new LinkedHashSet<>(); // 중복 제거 및 순서 유지

    if (productName == null || productName.isBlank()) {
      return new ArrayList<>();
    }

    // 브랜드
    String brand = BrandExtractor.extractBrand(productName);
    if (!brand.isEmpty()) {
      allKeywords.add(brand);
    }

    // 모델명들
    allKeywords.addAll(ModelExtractor.extractModels(productName));

    // 색상
    allKeywords.addAll(extractColors(productName));

    // 스펙
    allKeywords.addAll(extractSpecifications(productName));

    // 특징
    allKeywords.addAll(extractFeatures(productName));

    // N-gram (2-3단어 조합)
    allKeywords.addAll(extractNgrams(productName, 1, 3));

    // 개별 단어들도 추가
    String[] words = productName.trim().split("\\s+");
    for (String word : words) {
      String cleanWord = word.replaceAll("[^가-힣a-zA-Z0-9]", "");
      if (cleanWord.length() >= 2) { // 2글자 이상만
        allKeywords.add(cleanWord);
      }
    }

    return new ArrayList<>(allKeywords);
  }
}
