package com.yjlee.search.analysis.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.yjlee.search.analysis.service.TempIndexService;
import com.yjlee.search.config.IndexNameProvider;
import com.yjlee.search.test.base.BaseIntegrationTest;
import com.yjlee.search.test.config.TestAnalysisConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(TestAnalysisConfig.class)
class TempIndexServiceIntegrationTest extends BaseIntegrationTest {

  @Autowired private TempIndexService tempIndexService;
  @Autowired private IndexNameProvider indexNameProvider;

  @BeforeEach
  void setUp() throws Exception {
    deleteIndex(indexNameProvider.getTempAnalysisIndex());
  }

  @Test
  @DisplayName("임시 인덱스 생성")
  void shouldCreateTempIndex() throws Exception {
    assertThat(tempIndexService.isTempIndexExists()).isFalse();

    tempIndexService.refreshTempIndex();

    assertThat(tempIndexService.isTempIndexExists()).isTrue();
    assertThat(tempIndexService.getTempIndexName()).isEqualTo(indexNameProvider.getTempAnalysisIndex());
  }

  @Test
  @DisplayName("기존 인덱스 재생성")
  void shouldRecreateExistingIndex() throws Exception {
    tempIndexService.refreshTempIndex();
    assertThat(tempIndexService.isTempIndexExists()).isTrue();

    tempIndexService.refreshTempIndex();
    assertThat(tempIndexService.isTempIndexExists()).isTrue();
  }

  @Test
  @DisplayName("응답 객체와 함께 갱신")
  void shouldRefreshWithResponse() {
    var response = tempIndexService.refreshTempIndexWithResponse();

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo("success");
    assertThat(response.getIndexName()).isEqualTo(indexNameProvider.getTempAnalysisIndex());
    assertThat(tempIndexService.isTempIndexExists()).isTrue();
  }
}
