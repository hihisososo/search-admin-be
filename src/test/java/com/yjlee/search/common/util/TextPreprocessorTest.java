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
    String input = "Hello@World! 123#Test";
    String result = TextPreprocessor.preprocess(input);
    assertThat(result).isEqualTo("hello world 123 test");
  }

  @Test
  @DisplayName("한글 문자열 정상 처리")
  void should_process_korean_text_correctly() {
    String input = "안녕하세요!  테스트@입니다.";
    String result = TextPreprocessor.preprocess(input);
    assertThat(result).isEqualTo("안녕하세요 테스트 입니다");
  }
}
