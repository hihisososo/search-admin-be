package com.yjlee.search.common.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KoreanTextUtilsTest {

  @Test
  void 한글_자소_분해_테스트() {
    assertThat(KoreanTextUtils.decomposeHangul("안녕하세요"))
        .isEqualTo("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ");
    
    assertThat(KoreanTextUtils.decomposeHangul("테스트"))
        .isEqualTo("ㅌㅔㅅㅡㅌㅡ");
    
    assertThat(KoreanTextUtils.decomposeHangul("나이키"))
        .isEqualTo("ㄴㅏㅇㅣㅋㅣ");
    
    assertThat(KoreanTextUtils.decomposeHangul("test 한글"))
        .isEqualTo("test ㅎㅏㄴㄱㅡㄹ");
    
    assertThat(KoreanTextUtils.decomposeHangul("ABC123"))
        .isEqualTo("ABC123");
    
    assertThat(KoreanTextUtils.decomposeHangul(null))
        .isNull();
  }

  @Test
  void 한글_초성_추출_테스트() {
    assertThat(KoreanTextUtils.extractChosung("안녕하세요"))
        .isEqualTo("ㅇㄴㅎㅅㅇ");
    
    assertThat(KoreanTextUtils.extractChosung("테스트"))
        .isEqualTo("ㅌㅅㅌ");
    
    assertThat(KoreanTextUtils.extractChosung("나이키"))
        .isEqualTo("ㄴㅇㅋ");
    
    assertThat(KoreanTextUtils.extractChosung("test 나이키"))
        .isEqualTo("test ㄴㅇㅋ");
    
    assertThat(KoreanTextUtils.extractChosung("ABC 가나다"))
        .isEqualTo("ABC ㄱㄴㄷ");
    
    assertThat(KoreanTextUtils.extractChosung("123 라마바"))
        .isEqualTo("123 ㄹㅁㅂ");
    
    assertThat(KoreanTextUtils.extractChosung(null))
        .isNull();
  }

  @Test
  void 특수문자_포함_테스트() {
    assertThat(KoreanTextUtils.extractChosung("나이키@에어맥스"))
        .isEqualTo("ㄴㅇㅋ@ㅇㅇㅁㅅ");
    
    assertThat(KoreanTextUtils.decomposeHangul("나이키@에어맥스"))
        .isEqualTo("ㄴㅏㅇㅣㅋㅣ@ㅇㅔㅇㅓㅁㅐㄱㅅㅡ");
  }

  @Test
  void 공백_처리_테스트() {
    assertThat(KoreanTextUtils.extractChosung("나이키 에어 맥스"))
        .isEqualTo("ㄴㅇㅋ ㅇㅇ ㅁㅅ");
    
    assertThat(KoreanTextUtils.decomposeHangul("나이키 에어 맥스"))
        .isEqualTo("ㄴㅏㅇㅣㅋㅣ ㅇㅔㅇㅓ ㅁㅐㄱㅅㅡ");
  }

  @Test
  void 빈_문자열_테스트() {
    assertThat(KoreanTextUtils.extractChosung(""))
        .isEqualTo("");
    
    assertThat(KoreanTextUtils.decomposeHangul(""))
        .isEqualTo("");
  }

  @Test
  void 종성_있는_글자_테스트() {
    assertThat(KoreanTextUtils.decomposeHangul("강"))
        .isEqualTo("ㄱㅏㅇ");
    
    assertThat(KoreanTextUtils.decomposeHangul("힘"))
        .isEqualTo("ㅎㅣㅁ");
    
    assertThat(KoreanTextUtils.extractChosung("강남역"))
        .isEqualTo("ㄱㄴㅇ");
  }
}