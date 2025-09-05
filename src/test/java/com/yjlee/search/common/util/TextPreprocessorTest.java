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

  @Test
  @DisplayName("천단위 구분자 제거")
  void should_remove_thousand_separators() {
    assertThat(TextPreprocessor.preprocess("1,000원")).isEqualTo("1000원");
    assertThat(TextPreprocessor.preprocess("1,234,567개")).isEqualTo("1234567개");
    assertThat(TextPreprocessor.preprocess("10,000 ml")).isEqualTo("10000ml");
    assertThat(TextPreprocessor.preprocess("999,999,999")).isEqualTo("999999999");
  }

  @Test
  @DisplayName("확장된 단위 정규화 - 새로운 단위 포함")
  void should_normalize_extended_units() {
    // 무게 단위
    assertThat(TextPreprocessor.preprocess("100 g")).isEqualTo("100g");
    assertThat(TextPreprocessor.preprocess("1 kg")).isEqualTo("1kg");
    assertThat(TextPreprocessor.preprocess("500 mg")).isEqualTo("500mg");

    // 길이 단위
    assertThat(TextPreprocessor.preprocess("10 mm")).isEqualTo("10mm");
    assertThat(TextPreprocessor.preprocess("100 cm")).isEqualTo("100cm");
    assertThat(TextPreprocessor.preprocess("2 m")).isEqualTo("2m");
    assertThat(TextPreprocessor.preprocess("5 inch")).isEqualTo("5inch");

    // 한글 단위
    assertThat(TextPreprocessor.preprocess("삼각김밥 10 개")).isEqualTo("삼각김밥 10개");
    assertThat(TextPreprocessor.preprocess("휴지 30 단")).isEqualTo("휴지 30단");
    assertThat(TextPreprocessor.preprocess("라면 5 봉")).isEqualTo("라면 5봉");
    assertThat(TextPreprocessor.preprocess("우유 1 리터")).isEqualTo("우유 1리터");
    assertThat(TextPreprocessor.preprocess("커피 2 시간")).isEqualTo("커피 2시간");
    assertThat(TextPreprocessor.preprocess("대기 30 분")).isEqualTo("대기 30분");
  }

  @Test
  @DisplayName("모델명 보호 - 단위 뒤에 영/숫자가 있으면 합치지 않음")
  void should_protect_model_names() {
    // 단위 뒤에 영어가 있는 경우 - 합치지 않음
    assertThat(TextPreprocessor.preprocess("1 kgan")).isEqualTo("1 kgan");
    assertThat(TextPreprocessor.preprocess("10 mls")).isEqualTo("10 mls");
    assertThat(TextPreprocessor.preprocess("5 cm2")).isEqualTo("5 cm2");

    // 단위 뒤에 문자가 없는 경우 - 합침
    assertThat(TextPreprocessor.preprocess("1 kg 용기")).isEqualTo("1kg 용기");
    assertThat(TextPreprocessor.preprocess("10 ml.")).isEqualTo("10ml.");
  }

  @Test
  @DisplayName("연속 단위 분해")
  void should_separate_consecutive_units() {
    assertThat(TextPreprocessor.preprocess("10cm10kg10개")).isEqualTo("10cm 10kg 10개");
    assertThat(TextPreprocessor.preprocess("100ml500g")).isEqualTo("100ml 500g");
    assertThat(TextPreprocessor.preprocess("5kg10개")).isEqualTo("5kg 10개");
    assertThat(TextPreprocessor.preprocess("1l2병")).isEqualTo("1l 2병");
    assertThat(TextPreprocessor.preprocess("30cm50cm100cm")).isEqualTo("30cm 50cm 100cm");
  }

  @Test
  @DisplayName("크기 표시 분해 - x를 공백으로")
  void should_expand_size_notation() {
    // 3차원 크기
    assertThat(TextPreprocessor.preprocess("10x10x100cm")).isEqualTo("10 x 10 x 100cm");
    assertThat(TextPreprocessor.preprocess("5x5x5mm")).isEqualTo("5 x 5 x 5mm");
    assertThat(TextPreprocessor.preprocess("100x200x300")).isEqualTo("100 x 200 x 300");

    // 2차원 크기
    assertThat(TextPreprocessor.preprocess("10x20cm")).isEqualTo("10 x 20cm");
    assertThat(TextPreprocessor.preprocess("100x200mm")).isEqualTo("100 x 200mm");
    assertThat(TextPreprocessor.preprocess("30x40")).isEqualTo("30 x 40");
  }

  @Test
  @DisplayName("복합 전처리 - 모든 기능 통합")
  void should_process_complex_text() {
    assertThat(TextPreprocessor.preprocess("코카콜라 1,000 ml 10개입")).isEqualTo("코카콜라 1000ml 10개입");

    assertThat(TextPreprocessor.preprocess("상자 10x20x30cm 5 kg"))
        .isEqualTo("상자 10 x 20 x 30cm 5kg");

    assertThat(TextPreprocessor.preprocess("10cm20kg30개 1,000원")).isEqualTo("10cm 20kg 30개 1000원");

    assertThat(TextPreprocessor.preprocess("제품 1,234,567원 100 g x 10 개"))
        .isEqualTo("제품 1234567원 100g x 10개");
  }
}
