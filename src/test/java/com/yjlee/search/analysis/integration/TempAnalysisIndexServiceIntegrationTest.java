package com.yjlee.search.analysis.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.yjlee.search.analysis.service.TempIndexService;
import com.yjlee.search.index.provider.IndexNameProvider;
import com.yjlee.search.test.base.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TempAnalysisIndexServiceIntegrationTest extends BaseIntegrationTest {

  @Autowired private TempIndexService tempIndexService;
  @Autowired private IndexNameProvider indexNameProvider;

  @BeforeEach
  void setUp() throws Exception {
    deleteIndex(indexNameProvider.getTempAnalysisIndex());
  }

  @Test
  @DisplayName("임시 인덱스 생성")
  void createTempIndex() throws Exception {
    assertThat(tempIndexService.isTempIndexExists()).isFalse();

    tempIndexService.refreshTempIndex();

    assertThat(tempIndexService.isTempIndexExists()).isTrue();
    assertThat(tempIndexService.getTempIndexName())
        .isEqualTo(indexNameProvider.getTempAnalysisIndex());
  }

  @Test
  @DisplayName("기존 인덱스 재생성")
  void recreateExistingIndex() throws Exception {
    tempIndexService.refreshTempIndex();
    assertThat(tempIndexService.isTempIndexExists()).isTrue();

    tempIndexService.refreshTempIndex();
    assertThat(tempIndexService.isTempIndexExists()).isTrue();
  }
}
