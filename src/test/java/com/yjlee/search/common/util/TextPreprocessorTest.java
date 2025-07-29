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
  @DisplayName("빈 문자열 입력시 빈 문자열 반환")
  void should_return_empty_string_when_input_is_empty() {
    String result = TextPreprocessor.preprocess("");
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("공백만 있는 문자열 입력시 빈 문자열 반환")
  void should_return_empty_string_when_input_is_blank() {
    String result = TextPreprocessor.preprocess("   ");
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("특수문자 제거 및 소문자 변환")
  void should_remove_special_chars_and_convert_to_lowercase() {
    String input = "Hello@World! 123#Test";
    String result = TextPreprocessor.preprocess(input);
    assertThat(result).isEqualTo("hello world 123 test");
  }

  @Test
  @DisplayName("연속된 공백을 하나의 공백으로 변환")
  void should_replace_multiple_spaces_with_single_space() {
    String input = "Hello    World     Test";
    String result = TextPreprocessor.preprocess(input);
    assertThat(result).isEqualTo("hello world test");
  }

  @Test
  @DisplayName("앞뒤 공백 제거")
  void should_trim_leading_and_trailing_spaces() {
    String input = "  Hello World  ";
    String result = TextPreprocessor.preprocess(input);
    assertThat(result).isEqualTo("hello world");
  }

  @Test
  @DisplayName("한글 문자열 정상 처리")
  void should_process_korean_text_correctly() {
    String input = "안녕하세요! 테스트@입니다.";
    String result = TextPreprocessor.preprocess(input);
    assertThat(result).isEqualTo("안녕하세요 테스트 입니다");
  }

  @Test
  @DisplayName("유니코드 정규화 처리")
  void should_normalize_unicode() {
    String input = "café";
    String result = TextPreprocessor.preprocess(input);
    assertThat(result).isEqualTo("café");
  }

  @Test
  @DisplayName("숫자와 문자 혼합 처리")
  void should_handle_alphanumeric_text() {
    String input = "Product123 @Price$456";
    String result = TextPreprocessor.preprocess(input);
    assertThat(result).isEqualTo("product123 price 456");
  }
}
