package com.yjlee.search.index.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelExtractorTest {

  @Test
  @DisplayName("KRD-T155WEH1은 하나의 모델명으로 추출되어야 함 (최장일치)")
  void testLongestMatchForKRDModel() {
    String query = "KRD-T155WEH1";
    List<String> models = ModelExtractor.extractModelsForSearch(query);

    assertThat(models).hasSize(1);
    assertThat(models).containsExactly("krd-t155weh1");
  }

  @Test
  @DisplayName("여러 모델명이 포함된 쿼리")
  void testMultipleModels() {
    String query = "삼성 KRD-T155WEH1 LG GR-B607S";
    List<String> models = ModelExtractor.extractModelsForSearch(query);

    assertThat(models).containsExactlyInAnyOrder("krd-t155weh1", "gr-b607s");
  }

  @Test
  @DisplayName("하이픈 없는 모델명")
  void testModelWithoutHyphen() {
    String query = "SM951 256GB";
    List<String> models = ModelExtractor.extractModelsForSearch(query);

    assertThat(models).containsExactly("sm951");
  }

  @Test
  @DisplayName("중첩된 패턴에서 최장일치")
  void testOverlappingPatterns() {
    String query = "ABC-123DEF 테스트";
    List<String> models = ModelExtractor.extractModelsForSearch(query);

    // ABC-123DEF 전체가 매칭되고, 123DEF는 중복으로 추출되지 않아야 함
    assertThat(models).containsExactly("abc-123def");
  }
}
