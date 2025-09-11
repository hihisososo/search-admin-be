package com.yjlee.search;

import com.yjlee.search.common.util.KoreanTextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestChosungExtraction {
  private static final Logger log = LoggerFactory.getLogger(TestChosungExtraction.class);

  public static void main(String[] args) {
    String[] testWords = {"파이어쿠다", "나이키", "아디다스", "test 이용재"};

    log.debug("=== 초성 추출 테스트 ===");
    for (String word : testWords) {
      String chosung = KoreanTextUtils.extractChosung(word);
      log.debug("원본: {} -> 초성: {}", word, chosung);
    }

    log.debug("=== 자소 분해 테스트 ===");
    for (String word : testWords) {
      String jamo = KoreanTextUtils.decomposeHangul(word);
      log.debug("원본: {} -> 자소: {}", word, jamo);
    }

    log.debug("=== 검색 시나리오 ===");
    String searchKeyword = "ㅍㅇㅇㅋㄷ";
    String productName = "파이어쿠다";
    String extractedChosung = KoreanTextUtils.extractChosung(productName);

    log.debug("상품명: {}", productName);
    log.debug("추출된 초성: {}", extractedChosung);
    log.debug("검색어: {}", searchKeyword);
    log.debug("매칭 여부: {}", extractedChosung.startsWith(searchKeyword));
  }
}
