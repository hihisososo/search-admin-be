package com.yjlee.search.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KoreanTextUtilsTest {

  @Test
  @DisplayName("자소분리")
  void decomposeHangulTest() {
    assertThat(KoreanTextUtils.decomposeHangul("안녕하세요")).isEqualTo("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ");
    assertThat(KoreanTextUtils.decomposeHangul("테스트")).isEqualTo("ㅌㅔㅅㅡㅌㅡ");
    assertThat(KoreanTextUtils.decomposeHangul("나이키")).isEqualTo("ㄴㅏㅇㅣㅋㅣ");
    assertThat(KoreanTextUtils.decomposeHangul("test 한글")).isEqualTo("test ㅎㅏㄴㄱㅡㄹ");
    assertThat(KoreanTextUtils.decomposeHangul("ABC123")).isEqualTo("ABC123");
    assertThat(KoreanTextUtils.decomposeHangul(null)).isNull();
  }

  @Test
  @DisplayName("초성추출")
  void extractChosungTest() {
    assertThat(KoreanTextUtils.extractChosung("안녕하세요")).isEqualTo("ㅇㄴㅎㅅㅇ");
    assertThat(KoreanTextUtils.extractChosung("테스트")).isEqualTo("ㅌㅅㅌ");
    assertThat(KoreanTextUtils.extractChosung("나이키")).isEqualTo("ㄴㅇㅋ");
    assertThat(KoreanTextUtils.extractChosung("test 나이키")).isEqualTo("test ㄴㅇㅋ");
    assertThat(KoreanTextUtils.extractChosung("ABC 가나다")).isEqualTo("ABC ㄱㄴㄷ");
    assertThat(KoreanTextUtils.extractChosung("123 라마바")).isEqualTo("123 ㄹㅁㅂ");
    assertThat(KoreanTextUtils.extractChosung(null)).isNull();
  }

  @Test
  @DisplayName("특수문자 있을 경우")
  void specialCharacterTest() {
    assertThat(KoreanTextUtils.extractChosung("나이키@에어맥스")).isEqualTo("ㄴㅇㅋ@ㅇㅇㅁㅅ");
    assertThat(KoreanTextUtils.decomposeHangul("나이키@에어맥스")).isEqualTo("ㄴㅏㅇㅣㅋㅣ@ㅇㅔㅇㅓㅁㅐㄱㅅㅡ");
  }

  @Test
  @DisplayName("공백 처리")
  void whitespaceHandlingTest() {
    assertThat(KoreanTextUtils.extractChosung("나이키 에어 맥스")).isEqualTo("ㄴㅇㅋ ㅇㅇ ㅁㅅ");
    assertThat(KoreanTextUtils.decomposeHangul("나이키 에어 맥스")).isEqualTo("ㄴㅏㅇㅣㅋㅣ ㅇㅔㅇㅓ ㅁㅐㄱㅅㅡ");
  }

  @Test
  @DisplayName("빈 문자열")
  void emptyStringTest() {
    assertThat(KoreanTextUtils.extractChosung("")).isEqualTo("");
    assertThat(KoreanTextUtils.decomposeHangul("")).isEqualTo("");
  }

  @Test
  @DisplayName("종성도 잘 분리되는지ㅣ")
  void withJongsungTest() {
    assertThat(KoreanTextUtils.decomposeHangul("강")).isEqualTo("ㄱㅏㅇ");
    assertThat(KoreanTextUtils.decomposeHangul("힘")).isEqualTo("ㅎㅣㅁ");
    assertThat(KoreanTextUtils.extractChosung("강남역")).isEqualTo("ㄱㄴㅇ");
  }
}
