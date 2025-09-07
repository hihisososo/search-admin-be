package com.yjlee.search.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextPreprocessorTest {

  // ===== 기본 전처리 테스트 =====

  @Test
  @DisplayName("null 입력시 빈 문자열 반환")
  void should_return_empty_string_when_input_is_null() {
    assertThat(TextPreprocessor.preprocess(null)).isEmpty();
  }

  @Test
  @DisplayName("빈 문자열 처리")
  void should_handle_empty_strings() {
    assertThat(TextPreprocessor.preprocess("")).isEmpty();
    assertThat(TextPreprocessor.preprocess("   ")).isEmpty();
  }

  @Test
  @DisplayName("특수문자 제거 및 소문자 변환")
  void should_remove_special_chars_and_convert_to_lowercase() {
    assertThat(TextPreprocessor.preprocess("Hello@World!+-123#Test"))
        .isEqualTo("hello world +-123 test");
    assertThat(TextPreprocessor.preprocess("안녕하세요!  테스트@입니다.")).isEqualTo("안녕하세요 테스트 입니다.");
    assertThat(TextPreprocessor.preprocess("[정품] 애플 아이폰")).isEqualTo("정품 애플 아이폰");
  }

  @Test
  @DisplayName("보존되는 특수문자 - 슬래시, 대시, 플러스, 앰퍼샌드")
  void should_preserve_some_special_chars() {
    assertThat(TextPreprocessor.preprocess("전자제품/노트북&태블릿")).isEqualTo("전자제품/노트북&태블릿");
    assertThat(TextPreprocessor.preprocess("식품-음료+디저트")).isEqualTo("식품-음료+디저트");
  }

  // ===== 천단위 구분자 제거 =====

  @Test
  @DisplayName("천단위 구분자 제거")
  void should_remove_thousand_separators() {
    assertThat(TextPreprocessor.preprocess("1,000원")).isEqualTo("1000원");
    assertThat(TextPreprocessor.preprocess("1,234,567개")).isEqualTo("1234567개");
    assertThat(TextPreprocessor.preprocess("10,000 ml")).isEqualTo("10000 ml");
    assertThat(TextPreprocessor.preprocess("999,999,999")).isEqualTo("999999999");
  }

  @Test
  @DisplayName("천단위 구분자 경계 케이스")
  void should_handle_edge_cases_for_thousand_separators() {
    // 잘못된 천단위 구분자는 그대로 유지
    assertThat(TextPreprocessor.preprocess("1,23")).isEqualTo("1 23");
    assertThat(TextPreprocessor.preprocess("12,3456")).isEqualTo("12 3456");
    // 여러 개 연속
    assertThat(TextPreprocessor.preprocess("1,000,000,000,000")).isEqualTo("1000000000000");
  }

  // ===== 크기 표시 분해 (제거됨) =====

  @Test
  @DisplayName("크기 표시 - 더 이상 분해하지 않음")
  void should_not_expand_size_notation() {
    // 크기 표시를 분해하지 않고 그대로 유지
    assertThat(TextPreprocessor.preprocess("10x10x100cm")).isEqualTo("10x10x100cm");
    assertThat(TextPreprocessor.preprocess("5x5x5mm")).isEqualTo("5x5x5mm");
    assertThat(TextPreprocessor.preprocess("10x20cm")).isEqualTo("10x20cm");
    assertThat(TextPreprocessor.preprocess("100x200mm")).isEqualTo("100x200mm");
    assertThat(TextPreprocessor.preprocess("100x200x300")).isEqualTo("100x200x300");
    assertThat(TextPreprocessor.preprocess("30x40")).isEqualTo("30x40");
    assertThat(TextPreprocessor.preprocess("10x20cmpro")).isEqualTo("10x20cmpro");
    assertThat(TextPreprocessor.preprocess("10x20cm2")).isEqualTo("10x20cm2");
    assertThat(TextPreprocessor.preprocess("10x20cm-s")).isEqualTo("10x20cm-s");
    assertThat(TextPreprocessor.preprocess("10x20센티미터")).isEqualTo("10x20센티미터");
    assertThat(TextPreprocessor.preprocess("10x20센치입니다")).isEqualTo("10x20센치입니다");
    assertThat(TextPreprocessor.preprocess("10x100x개")).isEqualTo("10x100x개");
    assertThat(TextPreprocessor.preprocess("1.5x2.5cm")).isEqualTo("1.5x2.5cm");
    assertThat(TextPreprocessor.preprocess("0.5x0.75x1.25mm")).isEqualTo("0.5x0.75x1.25mm");
  }

  // ===== 연속 단위 분해 (제거됨) =====

  @Test
  @DisplayName("연속 단위 - 더 이상 분해하지 않음")
  void should_not_separate_consecutive_units() {
    // 연속 단위를 분해하지 않고 그대로 유지
    assertThat(TextPreprocessor.preprocess("10cm10kg10개")).isEqualTo("10cm10kg10개");
    assertThat(TextPreprocessor.preprocess("100ml500g")).isEqualTo("100ml500g");
    assertThat(TextPreprocessor.preprocess("5kg10개")).isEqualTo("5kg10개");
    assertThat(TextPreprocessor.preprocess("30cm50cm100cm")).isEqualTo("30cm50cm100cm");
    assertThat(TextPreprocessor.preprocess("10cm20")).isEqualTo("10cm20");
    assertThat(TextPreprocessor.preprocess("100ml500")).isEqualTo("100ml500");
    assertThat(TextPreprocessor.preprocess("10203040")).isEqualTo("10203040");
  }

  // ===== 복합 전처리 테스트 =====

  @Test
  @DisplayName("복합 전처리 - 천단위 구분자와 특수문자 제거")
  void should_process_complex_text() {
    // 단위 정규화, 크기 분해, 연속 단위 분해가 제거되어 원본 유지
    assertThat(TextPreprocessor.preprocess("코카콜라 1,000 ml 10개입")).isEqualTo("코카콜라 1000 ml 10개입");

    assertThat(TextPreprocessor.preprocess("상자 10x20x30cm 5 kg")).isEqualTo("상자 10x20x30cm 5 kg");

    assertThat(TextPreprocessor.preprocess("10cm20kg30개 1,000원")).isEqualTo("10cm20kg30개 1000원");

    assertThat(TextPreprocessor.preprocess("제품 1,234,567원 100 g x 10 개"))
        .isEqualTo("제품 1234567원 100 g x 10 개");
  }

  // ===== 경계 케이스 및 실패 가능 케이스 =====

  @Test
  @DisplayName("극단적으로 긴 숫자 처리")
  void should_handle_very_long_numbers() {
    assertThat(TextPreprocessor.preprocess("999,999,999,999,999,999"))
        .isEqualTo("999999999999999999");
    assertThat(TextPreprocessor.preprocess("0.000000000001 mg")).isEqualTo("0.000000000001 mg");
  }

  @Test
  @DisplayName("특수한 문자 조합")
  void should_handle_special_combinations() {
    // 대시와 숫자
    assertThat(TextPreprocessor.preprocess("제품-123-abc")).isEqualTo("제품-123-abc");
    // 여러 특수문자 연속
    assertThat(TextPreprocessor.preprocess("가격: 1,000원 / 할인: -20%")).isEqualTo("가격 1000원 / 할인 -20");
    // 괄호와 단위 - 단위 정규화 제거로 공백 유지
    assertThat(TextPreprocessor.preprocess("(100 ml) x 10 개")).isEqualTo("100 ml x 10 개");
  }

  @Test
  @DisplayName("단위명 처리 - 단위 정규화 제거로 원본 유지")
  void should_handle_unit_names() {
    // 단위 정규화가 제거되어 공백 유지
    assertThat(TextPreprocessor.preprocess("100 센티미터")).isEqualTo("100 센티미터");
    assertThat(TextPreprocessor.preprocess("100 미터")).isEqualTo("100 미터");
    assertThat(TextPreprocessor.preprocess("500 그램")).isEqualTo("500 그램");
    assertThat(TextPreprocessor.preprocess("500 킬로그램")).isEqualTo("500 킬로그램");
    assertThat(TextPreprocessor.preprocess("10 개가")).isEqualTo("10 개가");
    assertThat(TextPreprocessor.preprocess("5 병을")).isEqualTo("5 병을");
  }

  @Test
  @DisplayName("대소문자 혼용")
  void should_handle_mixed_case() {
    // 단위 정규화 제거로 공백 유지, 소문자만 변환
    assertThat(TextPreprocessor.preprocess("100 ML")).isEqualTo("100 ml");
    assertThat(TextPreprocessor.preprocess("5 Kg")).isEqualTo("5 kg");
    assertThat(TextPreprocessor.preprocess("10 CM")).isEqualTo("10 cm");
  }

  @Test
  @DisplayName("여러 처리 순서가 영향을 주는 케이스")
  void should_handle_processing_order_dependencies() {
    // 천단위 구분자만 제거, 크기 표시는 유지
    assertThat(TextPreprocessor.preprocess("1,000x2,000x3,000mm")).isEqualTo("1000x2000x3000mm");

    // 크기 표시와 연속 단위 모두 유지
    assertThat(TextPreprocessor.preprocess("10x20cm30x40mm")).isEqualTo("10x20cm30x40mm");

    // 모든 것이 섞인 경우 - 천단위 구분자만 제거
    assertThat(TextPreprocessor.preprocess("[특가] 1,234,567원 100x200x300cm 5kg10개입"))
        .isEqualTo("특가 1234567원 100x200x300cm 5kg10개입");
  }

  @Test
  @DisplayName("단위 바로 앞뒤 특수문자")
  void should_handle_special_chars_around_units() {
    // 특수문자만 제거, 단위 정규화 없음
    assertThat(TextPreprocessor.preprocess("(100ml)")).isEqualTo("100ml");
    assertThat(TextPreprocessor.preprocess("[5kg]")).isEqualTo("5kg");
    assertThat(TextPreprocessor.preprocess("*10개*")).isEqualTo("10개");
    assertThat(TextPreprocessor.preprocess("~30cm~")).isEqualTo("30cm");
  }

  @Test
  @DisplayName("실제 상품명 시나리오")
  void should_handle_real_product_scenarios() {
    // 단위 정규화, 크기 분해, 연속 단위 분해 제거로 공백 유지
    assertThat(TextPreprocessor.preprocess("LG 올레드 TV 55인치 (139.7cm)"))
        .isEqualTo("lg 올레드 tv 55인치 139.7cm");

    assertThat(TextPreprocessor.preprocess("삼성 갤럭시북3 프로 NT960XFG-K71A"))
        .isEqualTo("삼성 갤럭시북3 프로 nt960xfg-k71a");

    assertThat(TextPreprocessor.preprocess("농심 신라면 120g x 5개입 (총 600g)"))
        .isEqualTo("농심 신라면 120g x 5개입 총 600g");

    assertThat(TextPreprocessor.preprocess("코카콜라 제로 500ml*20EA")).isEqualTo("코카콜라 제로 500ml 20ea");
  }
}
