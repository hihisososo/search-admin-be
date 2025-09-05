package com.yjlee.search.index.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrandExtractorTest {

  @Test
  @DisplayName("상품명의 첫 단어를 브랜드로 추출")
  void should_extract_first_word_as_brand() {
    assertThat(BrandExtractor.extractBrand("삼성 갤럭시 노트북")).isEqualTo("삼성");
    assertThat(BrandExtractor.extractBrand("LG 올레드 TV")).isEqualTo("LG");
    assertThat(BrandExtractor.extractBrand("코카콜라 제로 500ml")).isEqualTo("코카콜라");
    assertThat(BrandExtractor.extractBrand("오뚜기 진라면 순한맛")).isEqualTo("오뚜기");
  }

  @Test
  @DisplayName("null 입력시 빈 문자열 반환")
  void should_return_empty_string_when_input_is_null() {
    assertThat(BrandExtractor.extractBrand(null)).isEmpty();
  }

  @Test
  @DisplayName("빈 문자열 입력시 빈 문자열 반환")
  void should_return_empty_string_when_input_is_empty() {
    assertThat(BrandExtractor.extractBrand("")).isEmpty();
    assertThat(BrandExtractor.extractBrand("   ")).isEmpty();
  }

  @Test
  @DisplayName("한 단어만 있는 경우")
  void should_extract_single_word() {
    assertThat(BrandExtractor.extractBrand("삼성")).isEqualTo("삼성");
    assertThat(BrandExtractor.extractBrand("Nike")).isEqualTo("Nike");
    assertThat(BrandExtractor.extractBrand("아이폰")).isEqualTo("아이폰");
  }

  @Test
  @DisplayName("앞뒤 공백 제거")
  void should_trim_whitespace() {
    assertThat(BrandExtractor.extractBrand("  삼성 갤럭시  ")).isEqualTo("삼성");
    assertThat(BrandExtractor.extractBrand("\t애플 아이폰\n")).isEqualTo("애플");
  }

  @Test
  @DisplayName("특수문자가 포함된 브랜드명")
  void should_extract_brand_with_special_chars() {
    assertThat(BrandExtractor.extractBrand("[삼성] 갤럭시 노트북")).isEqualTo("[삼성]");
    assertThat(BrandExtractor.extractBrand("(무료배송)LG 올레드")).isEqualTo("(무료배송)LG");
    assertThat(BrandExtractor.extractBrand("3M 포스트잇")).isEqualTo("3M");
    assertThat(BrandExtractor.extractBrand("A+ 등급 상품")).isEqualTo("A+");
  }

  @Test
  @DisplayName("숫자로 시작하는 브랜드명")
  void should_extract_numeric_brand() {
    assertThat(BrandExtractor.extractBrand("3M 청소도구")).isEqualTo("3M");
    assertThat(BrandExtractor.extractBrand("1+1 이벤트 상품")).isEqualTo("1+1");
    assertThat(BrandExtractor.extractBrand("365일 매장")).isEqualTo("365일");
  }

  @Test
  @DisplayName("여러 공백으로 구분된 경우")
  void should_handle_multiple_spaces() {
    assertThat(BrandExtractor.extractBrand("삼성    갤럭시    노트북")).isEqualTo("삼성");
    assertThat(BrandExtractor.extractBrand("LG\t\t올레드\n\nTV")).isEqualTo("LG");
  }

  @Test
  @DisplayName("대괄호 또는 괄호로 시작하는 경우")
  void should_extract_bracketed_brand() {
    assertThat(BrandExtractor.extractBrand("[공식] 삼성 갤럭시")).isEqualTo("[공식]");
    assertThat(BrandExtractor.extractBrand("(정품) 애플 아이폰")).isEqualTo("(정품)");
    assertThat(BrandExtractor.extractBrand("【신제품】 LG 냉장고")).isEqualTo("【신제품】");
  }

  @Test
  @DisplayName("복합 브랜드명")
  void should_extract_compound_brand() {
    assertThat(BrandExtractor.extractBrand("CJ제일제당 비비고 만두")).isEqualTo("CJ제일제당");
    assertThat(BrandExtractor.extractBrand("롯데칠성음료 칠성사이다")).isEqualTo("롯데칠성음료");
    assertThat(BrandExtractor.extractBrand("동아제약 박카스")).isEqualTo("동아제약");
  }
}
