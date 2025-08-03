package com.yjlee.search.clicklog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.clicklog.dto.ClickLogResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickLogServiceTest {

  @Mock private ElasticsearchClient elasticsearchClient;

  @Mock private IndexResponse indexResponse;

  @InjectMocks private ClickLogService clickLogService;

  private ClickLogRequest clickLogRequest;

  @BeforeEach
  void setUp() {
    clickLogRequest =
        ClickLogRequest.builder()
            .searchKeyword("노트북")
            .clickedProductId("PROD-12345")
            .indexName("products")
            .build();
  }

  @Test
  @DisplayName("클릭 로그 저장 - 성공")
  void logClick_Success() throws Exception {
    // given
    when(elasticsearchClient.index(any(IndexRequest.class))).thenReturn(indexResponse);

    // when
    ClickLogResponse response = clickLogService.logClick(clickLogRequest);

    // then
    assertThat(response).isNotNull();
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getMessage()).contains("성공적으로 저장");
    assertThat(response.getTimestamp()).isNotNull();

    verify(elasticsearchClient, times(1)).index(any(IndexRequest.class));
  }

  @Test
  @DisplayName("클릭 로그 저장 - 실패")
  void logClick_Failure() throws Exception {
    // given
    when(elasticsearchClient.index(any(IndexRequest.class)))
        .thenThrow(new RuntimeException("Elasticsearch 연결 오류"));

    // when
    ClickLogResponse response = clickLogService.logClick(clickLogRequest);

    // then
    assertThat(response).isNotNull();
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).contains("오류가 발생했습니다");
    assertThat(response.getMessage()).contains("Elasticsearch 연결 오류");
    assertThat(response.getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("클릭 로그 저장 - null 요청 처리")
  void logClick_NullRequest() {
    // when
    ClickLogResponse response = clickLogService.logClick(null);

    // then
    assertThat(response).isNotNull();
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).contains("오류가 발생했습니다");
  }
}
