package com.yjlee.search;

import com.yjlee.search.common.util.KoreanTextUtils;

public class TestChosungExtraction {
  public static void main(String[] args) {
    String[] testWords = {
      "파이어쿠다",
      "나이키",
      "아디다스",
      "test 이용재"
    };
    
    System.out.println("=== 초성 추출 테스트 ===");
    for (String word : testWords) {
      String chosung = KoreanTextUtils.extractChosung(word);
      System.out.printf("원본: %s -> 초성: %s%n", word, chosung);
    }
    
    System.out.println("\n=== 자소 분해 테스트 ===");
    for (String word : testWords) {
      String jamo = KoreanTextUtils.decomposeHangul(word);
      System.out.printf("원본: %s -> 자소: %s%n", word, jamo);
    }
    
    System.out.println("\n=== 검색 시나리오 ===");
    String searchKeyword = "ㅍㅇㅇㅋㄷ";
    String productName = "파이어쿠다";
    String extractedChosung = KoreanTextUtils.extractChosung(productName);
    
    System.out.printf("상품명: %s%n", productName);
    System.out.printf("추출된 초성: %s%n", extractedChosung);
    System.out.printf("검색어: %s%n", searchKeyword);
    System.out.printf("매칭 여부: %s%n", extractedChosung.startsWith(searchKeyword));
  }
}