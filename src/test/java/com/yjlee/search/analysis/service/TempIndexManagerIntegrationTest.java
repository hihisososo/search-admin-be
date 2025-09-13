package com.yjlee.search.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yjlee.search.analysis.config.TestAnalysisConfig;
import com.yjlee.search.common.config.LocalElasticsearchTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(TestAnalysisConfig.class)
class TempIndexManagerIntegrationTest extends LocalElasticsearchTest {

  @Autowired private TempIndexManager tempIndexManager;

  private static final String TEMP_INDEX_NAME = "temp-analysis-current";

  @BeforeEach
  void setUp() throws Exception {
    deleteIndex(TEMP_INDEX_NAME);
  }

  @Test
  @DisplayName("임시 인덱스 생성")
  void shouldCreateTempIndex() throws Exception {
    assertThat(tempIndexManager.isTempIndexExists()).isFalse();

    tempIndexManager.refreshTempIndex();

    assertThat(tempIndexManager.isTempIndexExists()).isTrue();
    assertThat(tempIndexManager.getTempIndexName()).isEqualTo(TEMP_INDEX_NAME);
  }

  @Test
  @DisplayName("기존 인덱스 재생성")
  void shouldRecreateExistingIndex() throws Exception {
    tempIndexManager.refreshTempIndex();
    assertThat(tempIndexManager.isTempIndexExists()).isTrue();

    tempIndexManager.refreshTempIndex();

    assertThat(tempIndexManager.isTempIndexExists()).isTrue();
  }

  @Test
  @DisplayName("응답 객체와 함께 갱신")
  void shouldRefreshWithResponse() {
    var response = tempIndexManager.refreshTempIndexWithResponse();

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo("success");
    assertThat(response.getMessage()).contains("성공적으로 갱신");
    assertThat(response.getIndexName()).isEqualTo(TEMP_INDEX_NAME);
    assertThat(tempIndexManager.isTempIndexExists()).isTrue();
  }
}
