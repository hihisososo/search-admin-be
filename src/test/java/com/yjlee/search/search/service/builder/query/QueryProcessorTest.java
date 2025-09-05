package com.yjlee.search.search.service.builder.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yjlee.search.search.service.builder.model.ProcessedQuery;
import com.yjlee.search.search.service.builder.model.QueryContext;
import com.yjlee.search.search.service.typo.TypoCorrectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryProcessorTest {

  @Mock private TypoCorrectionService typoCorrectionService;

  private QueryProcessor queryProcessor;

  @BeforeEach
  void setUp() {
    queryProcessor = new QueryProcessor(typoCorrectionService);
  }

  @Test
  @DisplayName("쿼리 전처리 - 단위 정규화 및 소문자 변환")
  void should_preprocess_query() {
    ProcessedQuery result = queryProcessor.processQuery("코카콜라 500 ML", false);

    assertThat(result.getOriginal()).isEqualTo("코카콜라 500 ML");
    assertThat(result.getFinalQuery()).isEqualTo("코카콜라 500ml");
    verify(typoCorrectionService, never()).applyTypoCorrection(anyString());
  }

  @Test
  @DisplayName("쿼리 전처리 - 천단위 구분자 제거")
  void should_remove_thousand_separators() {
    ProcessedQuery result = queryProcessor.processQuery("1,000원 상품", false);

    assertThat(result.getFinalQuery()).isEqualTo("1000원 상품");
  }

  @Test
  @DisplayName("쿼리 전처리 - 크기 표시 분해")
  void should_expand_size_notation() {
    ProcessedQuery result = queryProcessor.processQuery("상자 10x20x30cm", false);

    assertThat(result.getFinalQuery()).isEqualTo("상자 10 x 20 x 30cm");
  }

  @Test
  @DisplayName("쿼리 전처리 - 연속 단위 분해")
  void should_separate_consecutive_units() {
    ProcessedQuery result = queryProcessor.processQuery("10cm20kg30개", false);

    assertThat(result.getFinalQuery()).isEqualTo("10cm 20kg 30개");
  }

  @Test
  @DisplayName("오타교정 적용")
  void should_apply_typo_correction_when_enabled() {
    when(typoCorrectionService.applyTypoCorrection("쌈성 갤럭시")).thenReturn("삼성 갤럭시");

    ProcessedQuery result = queryProcessor.processQuery("쌈성 갤럭시", true);

    assertThat(result.getFinalQuery()).isEqualTo("삼성 갤럭시");
    verify(typoCorrectionService).applyTypoCorrection("쌈성 갤럭시");
  }

  @Test
  @DisplayName("오타교정 비활성화")
  void should_not_apply_typo_correction_when_disabled() {
    ProcessedQuery result = queryProcessor.processQuery("쌈성 갤럭시", false);

    assertThat(result.getFinalQuery()).isEqualTo("쌈성 갤럭시");
    verify(typoCorrectionService, never()).applyTypoCorrection(anyString());
  }

  @Test
  @DisplayName("오타교정 null인 경우 기본값 false")
  void should_not_apply_typo_correction_when_null() {
    ProcessedQuery result = queryProcessor.processQuery("테스트", null);

    assertThat(result.getFinalQuery()).isEqualTo("테스트");
    verify(typoCorrectionService, never()).applyTypoCorrection(anyString());
  }

  @Test
  @DisplayName("null 쿼리 처리")
  void should_handle_null_query() {
    ProcessedQuery result = queryProcessor.processQuery(null, false);

    assertThat(result.getOriginal()).isEmpty();
    assertThat(result.getFinalQuery()).isEmpty();
  }

  @Test
  @DisplayName("빈 쿼리 처리")
  void should_handle_empty_query() {
    ProcessedQuery result = queryProcessor.processQuery("", false);

    assertThat(result.getOriginal()).isEmpty();
    assertThat(result.getFinalQuery()).isEmpty();
  }

  @Test
  @DisplayName("공백만 있는 쿼리 처리")
  void should_handle_whitespace_only_query() {
    ProcessedQuery result = queryProcessor.processQuery("   ", false);

    assertThat(result.getFinalQuery()).isEmpty();
  }

  @Test
  @DisplayName("특수문자 제거")
  void should_remove_special_characters() {
    ProcessedQuery result = queryProcessor.processQuery("삼성@갤럭시#노트북!", false);

    assertThat(result.getFinalQuery()).isEqualTo("삼성 갤럭시 노트북");
  }

  @Test
  @DisplayName("의미있는 특수문자 보존")
  void should_preserve_meaningful_special_chars() {
    ProcessedQuery result = queryProcessor.processQuery("A+ 등급 / B-타입 & C+모델", false);

    assertThat(result.getFinalQuery()).isEqualTo("a+ 등급 / b-타입 & c+모델");
  }

  @Test
  @DisplayName("한글 단위 정규화")
  void should_normalize_korean_units() {
    ProcessedQuery result = queryProcessor.processQuery("라면 5 개입", false);

    assertThat(result.getFinalQuery()).isEqualTo("라면 5개입");
  }

  @Test
  @DisplayName("analyzeQuery - 빈 쿼리")
  void should_analyze_empty_query() {
    QueryContext context = queryProcessor.analyzeQuery("", false);

    assertThat(context.getOriginalQuery()).isEmpty();
    assertThat(context.getProcessedQuery()).isEmpty();
    assertThat(context.getQueryWithoutTerms()).isEmpty();
    assertThat(context.isQueryEmptyAfterRemoval()).isTrue();
  }

  @Test
  @DisplayName("analyzeQuery - null 쿼리")
  void should_analyze_null_query() {
    QueryContext context = queryProcessor.analyzeQuery(null, false);

    assertThat(context.getOriginalQuery()).isEmpty();
    assertThat(context.getProcessedQuery()).isEmpty();
    assertThat(context.isQueryEmptyAfterRemoval()).isTrue();
  }

  @Test
  @DisplayName("analyzeQuery - 정상 쿼리 분석")
  void should_analyze_normal_query() {
    QueryContext context = queryProcessor.analyzeQuery("삼성 갤럭시 500 ml", false);

    assertThat(context.getOriginalQuery()).isEqualTo("삼성 갤럭시 500 ml");
    assertThat(context.getProcessedQuery()).isEqualTo("삼성 갤럭시 500ml");
    assertThat(context.getQueryWithoutTerms()).isEqualTo("삼성 갤럭시 500ml");
    assertThat(context.isQueryEmptyAfterRemoval()).isFalse();
    assertThat(context.getApplyTypoCorrection()).isFalse();
  }

  @Test
  @DisplayName("analyzeQuery - 오타교정 포함 분석")
  void should_analyze_query_with_typo_correction() {
    when(typoCorrectionService.applyTypoCorrection("쌈성 갤럭시")).thenReturn("삼성 갤럭시");

    QueryContext context = queryProcessor.analyzeQuery("쌈성 갤럭시", true);

    assertThat(context.getOriginalQuery()).isEqualTo("쌈성 갤럭시");
    assertThat(context.getProcessedQuery()).isEqualTo("삼성 갤럭시");
    assertThat(context.getApplyTypoCorrection()).isTrue();
  }

  @Test
  @DisplayName("복합 전처리 - 모든 기능 통합")
  void should_handle_complex_preprocessing() {
    ProcessedQuery result = queryProcessor.processQuery("LG OLED TV 1,000,000원 55 inch", false);

    assertThat(result.getFinalQuery()).isEqualTo("lg oled tv 1000000원 55inch");
  }

  @Test
  @DisplayName("오타교정과 전처리 동시 적용")
  void should_apply_both_typo_correction_and_preprocessing() {
    when(typoCorrectionService.applyTypoCorrection("엘쥐 올레드 tv 1000000원 55inch"))
        .thenReturn("엘지 올레드 tv 1000000원 55inch");

    ProcessedQuery result = queryProcessor.processQuery("엘쥐 OLED TV 1,000,000원 55 inch", true);

    assertThat(result.getFinalQuery()).isEqualTo("엘지 올레드 tv 1000000원 55inch");
  }
}
