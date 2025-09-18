package com.yjlee.search.deployment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchIndexServiceTest {

  @Mock private ElasticsearchClient elasticsearchClient;
  @Mock private ElasticsearchIndicesClient indicesClient;

  private ElasticsearchIndexService elasticsearchIndexService;

  @BeforeEach
  void setUp() throws Exception {
    elasticsearchIndexService = new ElasticsearchIndexService(elasticsearchClient);
    when(elasticsearchClient.indices()).thenReturn(indicesClient);
  }

  @Test
  @DisplayName("인덱스 생성 성공")
  void createIndexSuccess() throws Exception {
    String indexName = "products_v202401011200";
    String mappingJson = "{\"properties\":{\"name\":{\"type\":\"text\"}}}";
    String settingsJson = "{\"number_of_shards\":1}";

    CreateIndexResponse createResponse = mock(CreateIndexResponse.class);
    when(indicesClient.create(any(CreateIndexRequest.class))).thenReturn(createResponse);

    assertThatCode(() -> elasticsearchIndexService.createIndex(indexName, mappingJson, settingsJson))
        .doesNotThrowAnyException();

    verify(indicesClient).create(any(CreateIndexRequest.class));
  }

  @Test
  @DisplayName("인덱스 생성 중 예외 발생")
  void createIndexException() throws Exception {
    String indexName = "products_v202401011200";
    String mappingJson = "{\"properties\":{}}";
    String settingsJson = "{\"number_of_shards\":1}";

    when(indicesClient.create(any(CreateIndexRequest.class)))
        .thenThrow(new RuntimeException("Elasticsearch 연결 실패"));

    assertThatThrownBy(() -> elasticsearchIndexService.createIndex(indexName, mappingJson, settingsJson))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("인덱스 생성 중 에러");
  }

  @Test
  @DisplayName("인덱스 존재 여부 확인 - 존재하는 경우")
  void checkIndexExists() throws Exception {
    BooleanResponse trueResponse = mock(BooleanResponse.class);
    when(trueResponse.value()).thenReturn(true);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(trueResponse);

    boolean exists = elasticsearchIndexService.indexExists("products_v202401011200");

    assertThat(exists).isTrue();
    verify(indicesClient).exists(any(ExistsRequest.class));
  }

  @Test
  @DisplayName("인덱스 존재 여부 확인 - 존재하지 않는 경우")
  void checkIndexNotExists() throws Exception {
    BooleanResponse falseResponse = mock(BooleanResponse.class);
    when(falseResponse.value()).thenReturn(false);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(falseResponse);

    boolean exists = elasticsearchIndexService.indexExists("products_v202401011200");

    assertThat(exists).isFalse();
    verify(indicesClient).exists(any(ExistsRequest.class));
  }

  @Test
  @DisplayName("인덱스 삭제 - 존재하는 경우")
  void deleteIndexIfExists() throws Exception {
    BooleanResponse trueResponse = mock(BooleanResponse.class);
    when(trueResponse.value()).thenReturn(true);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(trueResponse);

    DeleteIndexResponse deleteResponse = mock(DeleteIndexResponse.class);
    when(indicesClient.delete(any(DeleteIndexRequest.class))).thenReturn(deleteResponse);

    elasticsearchIndexService.deleteIndexIfExists("products_v202401011200");

    verify(indicesClient).exists(any(ExistsRequest.class));
    verify(indicesClient).delete(any(DeleteIndexRequest.class));
  }

  @Test
  @DisplayName("인덱스 삭제 - 존재하지 않는 경우")
  void deleteIndexIfNotExists() throws Exception {
    BooleanResponse falseResponse = mock(BooleanResponse.class);
    when(falseResponse.value()).thenReturn(false);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(falseResponse);

    elasticsearchIndexService.deleteIndexIfExists("products_v202401011200");

    verify(indicesClient).exists(any(ExistsRequest.class));
    verify(indicesClient, never()).delete(any(DeleteIndexRequest.class));
  }

  @Test
  @DisplayName("인덱스 존재 확인 중 예외 발생")
  void indexExistsException() throws Exception {
    when(indicesClient.exists(any(ExistsRequest.class)))
        .thenThrow(new RuntimeException("Elasticsearch 연결 실패"));

    assertThatThrownBy(() -> elasticsearchIndexService.indexExists("products_v202401011200"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("ES 연결 중 에러");
  }

  @Test
  @DisplayName("인덱스 삭제 중 예외 발생")
  void deleteIndexException() throws Exception {
    BooleanResponse trueResponse = mock(BooleanResponse.class);
    when(trueResponse.value()).thenReturn(true);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(trueResponse);

    when(indicesClient.delete(any(DeleteIndexRequest.class)))
        .thenThrow(new RuntimeException("Elasticsearch 연결 실패"));

    assertThatThrownBy(() -> elasticsearchIndexService.deleteIndexIfExists("products_v202401011200"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("ES 연결 중 에러");
  }
}