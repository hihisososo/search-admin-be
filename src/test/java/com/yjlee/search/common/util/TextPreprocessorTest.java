package com.yjlee.search.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextPreprocessorTest {

  @Test
  @DisplayName("null 입력시 빈 문자열 반환")
  void should_return_empty_string_when_input_is_null() {
    String result = TextPreprocessor.preprocess(null);
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("특수문자 제거 및 소문자 변환")
  void should_remove_special_chars_and_convert_to_lowercase() {
    String input = "Hello@World!+-123#Test";
    String result = TextPreprocessor.preprocess(input);
    assertThat(result).isEqualTo("hello world +-123 test");
  }

  @Test
  @DisplayName("한글 문자열 정상 처리")
  void should_process_korean_text_correctly() {
    String input = "안녕하세요!  테스트@입니다.";
    String result = TextPreprocessor.preprocess(input);
    assertThat(result).isEqualTo("안녕하세요 테스트 입니다.");
  }

  @Test
  @DisplayName("용량 단위 정규화 - 공백 제거")
  void should_normalize_volume_units() {
    assertThat(TextPreprocessor.normalizeUnits("코카콜라 500 ml")).isEqualTo("코카콜라 500ml");
    assertThat(TextPreprocessor.normalizeUnits("생수 1.5 L")).isEqualTo("생수 1.5L");
    assertThat(TextPreprocessor.normalizeUnits("음료 350 CC")).isEqualTo("음료 350CC");
    assertThat(TextPreprocessor.normalizeUnits("주스 12 oz")).isEqualTo("주스 12oz");
    assertThat(TextPreprocessor.normalizeUnits("우유 1 gal")).isEqualTo("우유 1gal");
  }

  @Test
  @DisplayName("수량 단위 정규화 - 공백 제거")
  void should_normalize_quantity_units() {
    assertThat(TextPreprocessor.normalizeUnits("휴지 30 개입")).isEqualTo("휴지 30개입");
    assertThat(TextPreprocessor.normalizeUnits("비타민 100 정")).isEqualTo("비타민 100정");
    assertThat(TextPreprocessor.normalizeUnits("마스크 50 장")).isEqualTo("마스크 50장");
    assertThat(TextPreprocessor.normalizeUnits("라면 5 봉지")).isEqualTo("라면 5봉지");
    assertThat(TextPreprocessor.normalizeUnits("세제 3 팩")).isEqualTo("세제 3팩");
    assertThat(TextPreprocessor.normalizeUnits("선물 2 세트")).isEqualTo("선물 2세트");
    assertThat(TextPreprocessor.normalizeUnits("양말 10 켤레")).isEqualTo("양말 10켤레");
    assertThat(TextPreprocessor.normalizeUnits("소주 20 병")).isEqualTo("소주 20병");
    assertThat(TextPreprocessor.normalizeUnits("콜라 6 캔")).isEqualTo("콜라 6캔");
  }

  @Test
  @DisplayName("복합 단위 정규화")
  void should_normalize_multiple_units() {
    assertThat(TextPreprocessor.normalizeUnits("코카콜라 500 ml x 20 개")).isEqualTo("코카콜라 500ml x 20개");
    assertThat(TextPreprocessor.normalizeUnits("생수 2 L 6 병 세트")).isEqualTo("생수 2L 6병 세트");
  }

  @Test
  @DisplayName("단위가 없는 경우 그대로 반환")
  void should_return_unchanged_when_no_units() {
    assertThat(TextPreprocessor.normalizeUnits("일반 텍스트")).isEqualTo("일반 텍스트");
    assertThat(TextPreprocessor.normalizeUnits("숫자 123 있음")).isEqualTo("숫자 123 있음");
  }

  @Test
  @DisplayName("null 또는 빈 문자열 처리")
  void should_handle_null_or_empty() {
    assertThat(TextPreprocessor.normalizeUnits(null)).isEmpty();
    assertThat(TextPreprocessor.normalizeUnits("")).isEmpty();
    assertThat(TextPreprocessor.normalizeUnits("   ")).isEmpty();
  }

  @Test
  @DisplayName("단위 추출 - 공백으로 구분")
  void should_extract_units() {
    assertThat(TextPreprocessor.extractUnits("젤리 10 입 10 개")).isEqualTo("10개");
    assertThat(TextPreprocessor.extractUnits("코카콜라 500 ml")).isEqualTo("500ml");
    assertThat(TextPreprocessor.extractUnits("휴지 30 개입 세트")).isEqualTo("30개입");
    assertThat(TextPreprocessor.extractUnits("비타민 100 정 x 3 병")).isEqualTo("100정 3병");
    assertThat(TextPreprocessor.extractUnits("콜라 500 ml 20 캔")).isEqualTo("500ml 20캔");
  }

  @Test
  @DisplayName("단위 추출 - 붙어있는 경우")
  void should_extract_units_without_space() {
    assertThat(TextPreprocessor.extractUnits("10ml냉장고")).isEqualTo("10ml");
    assertThat(TextPreprocessor.extractUnits("30개입")).isEqualTo("30개입");
    assertThat(TextPreprocessor.extractUnits("500g포장")).isEqualTo("500g");
    assertThat(TextPreprocessor.extractUnits("2L들이")).isEqualTo("2l");
    assertThat(TextPreprocessor.extractUnits("1MLS")).isEmpty();
  }

  @Test
  @DisplayName("단위가 없는 경우 빈 문자열 반환")
  void should_return_empty_when_no_units_to_extract() {
    assertThat(TextPreprocessor.extractUnits("일반 텍스트")).isEmpty();
    assertThat(TextPreprocessor.extractUnits("노트북")).isEmpty();
    assertThat(TextPreprocessor.extractUnits("")).isEmpty();
  }
}
