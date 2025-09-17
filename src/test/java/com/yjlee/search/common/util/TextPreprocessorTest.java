package com.yjlee.search.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextPreprocessorTest {

  @Test
  @DisplayName("null 입력시 빈 문자열 반환")
  void returnEmptyStringForNullInput() {
    assertThat(TextPreprocessor.preprocess(null)).isEmpty();
  }

  @Test
  @DisplayName("빈 문자열 처리")
  void handleEmptyStrings() {
    assertThat(TextPreprocessor.preprocess("")).isEmpty();
    assertThat(TextPreprocessor.preprocess("   ")).isEmpty();
  }

  @Test
  @DisplayName("특수문자 제거")
  void removeSpecialCharsAndConvertToLowercase() {
    assertThat(TextPreprocessor.preprocess("Hello@World!+-123#Test"))
        .isEqualTo("hello world +-123 test");
    assertThat(TextPreprocessor.preprocess("안녕하세요!  테스트@입니다.")).isEqualTo("안녕하세요 테스트 입니다.");
    assertThat(TextPreprocessor.preprocess("[정품] 애플 아이폰")).isEqualTo("정품 애플 아이폰");
  }

  @Test
  @DisplayName("특문 유지")
  void preserveSomeSpecialChars() {
    assertThat(TextPreprocessor.preprocess("전자제품/노트북&태블릿")).isEqualTo("전자제품/노트북&태블릿");
    assertThat(TextPreprocessor.preprocess("식품-음료+디저트")).isEqualTo("식품-음료+디저트");
  }

  @Test
  @DisplayName("천단위 구분자 제거")
  void removeThousandSeparators() {
    assertThat(TextPreprocessor.preprocess("1,000원")).isEqualTo("1000원");
    assertThat(TextPreprocessor.preprocess("1,234,567개")).isEqualTo("1234567개");
    assertThat(TextPreprocessor.preprocess("10,000 ml")).isEqualTo("10000 ml");
    assertThat(TextPreprocessor.preprocess("999,999,999")).isEqualTo("999999999");
    assertThat(TextPreprocessor.preprocess("1,23")).isEqualTo("1 23");
    assertThat(TextPreprocessor.preprocess("12,3456")).isEqualTo("12 3456");
    assertThat(TextPreprocessor.preprocess("1,000,000,000,000")).isEqualTo("1000000000000");
  }

  @Test
  @DisplayName("실제 상품명")
  void handleRealProductScenarios() {
    assertThat(TextPreprocessor.preprocess("LG 올레드 TV 55인치 (139.7cm)"))
        .isEqualTo("lg 올레드 tv 55인치 139.7cm");
    assertThat(TextPreprocessor.preprocess("삼성 갤럭시북3 프로 NT960XFG-K71A"))
        .isEqualTo("삼성 갤럭시북3 프로 nt960xfg-k71a");
    assertThat(TextPreprocessor.preprocess("농심 신라면 120g x 5개입 (총 600g)"))
        .isEqualTo("농심 신라면 120g x 5개입 총 600g");
    assertThat(TextPreprocessor.preprocess("코카콜라 제로 500ml*20EA")).isEqualTo("코카콜라 제로 500ml 20ea");
  }
}
