package com.yjlee.search.deployment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.yjlee.search.deployment.domain.IndexingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchIndexServiceTest {

  @Mock private ElasticsearchClient elasticsearchClient;
  @Mock private ElasticsearchIndicesClient indicesClient;
  @Mock private ElasticsearchSettingsService settingsService;
  @Mock private ElasticsearchMappingService mappingService;

  @InjectMocks private ElasticsearchIndexService elasticsearchIndexService;

  private IndexingContext indexingContext;

  @BeforeEach
  void setUp() throws Exception {
    when(elasticsearchClient.indices()).thenReturn(indicesClient);

    indexingContext = mock(IndexingContext.class);
    when(indexingContext.getVersion()).thenReturn("v202401011200");
    when(indexingContext.getProductIndexName()).thenReturn("products_v202401011200");
    when(indexingContext.getAutocompleteIndexName()).thenReturn("products_ac_v202401011200");
    when(indexingContext.getSynonymSetName()).thenReturn("synonym_v202401011200");
  }

  @Test
  @DisplayName("새 인덱스 생성 성공")
  void createNewIndexSuccess() throws Exception {
    when(mappingService.loadProductMapping()).thenReturn("{\"properties\":{}}");
    when(mappingService.loadAutocompleteMapping()).thenReturn("{\"properties\":{}}");
    when(settingsService.createProductIndexSettings(any(), any()))
        .thenReturn("{\"number_of_shards\":1}");
    when(settingsService.createAutocompleteIndexSettings(any()))
        .thenReturn("{\"number_of_shards\":1}");

    BooleanResponse falseResponse = mock(BooleanResponse.class);
    when(falseResponse.value()).thenReturn(false);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(falseResponse);

    CreateIndexResponse createResponse = mock(CreateIndexResponse.class);
    when(createResponse.acknowledged()).thenReturn(true);
    when(indicesClient.create(any(CreateIndexRequest.class))).thenReturn(createResponse);

    String result = elasticsearchIndexService.createNewIndex(indexingContext);

    assertThat(result).isEqualTo("products_v202401011200");
    verify(indicesClient, times(2)).create(any(CreateIndexRequest.class));
  }

  @Test
  @DisplayName("기존 인덱스 삭제 후 생성")
  void deleteExistingIndexBeforeCreate() throws Exception {
    when(mappingService.loadProductMapping()).thenReturn("{\"properties\":{}}");
    when(mappingService.loadAutocompleteMapping()).thenReturn("{\"properties\":{}}");
    when(settingsService.createProductIndexSettings(any(), any()))
        .thenReturn("{\"number_of_shards\":1}");
    when(settingsService.createAutocompleteIndexSettings(any()))
        .thenReturn("{\"number_of_shards\":1}");

    BooleanResponse trueResponse = mock(BooleanResponse.class);
    when(trueResponse.value()).thenReturn(true);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(trueResponse);

    DeleteIndexResponse deleteResponse = mock(DeleteIndexResponse.class);
    when(deleteResponse.acknowledged()).thenReturn(true);
    when(indicesClient.delete(any(DeleteIndexRequest.class))).thenReturn(deleteResponse);

    CreateIndexResponse createResponse = mock(CreateIndexResponse.class);
    when(createResponse.acknowledged()).thenReturn(true);
    when(indicesClient.create(any(CreateIndexRequest.class))).thenReturn(createResponse);

    String result = elasticsearchIndexService.createNewIndex(indexingContext);

    assertThat(result).isEqualTo("products_v202401011200");
    verify(indicesClient, times(2)).delete(any(DeleteIndexRequest.class));
    verify(indicesClient, times(2)).create(any(CreateIndexRequest.class));
  }

  @Test
  @DisplayName("인덱스 존재 여부 확인")
  void checkIndexExists() throws Exception {
    BooleanResponse trueResponse = mock(BooleanResponse.class);
    when(trueResponse.value()).thenReturn(true);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(trueResponse);

    boolean exists = elasticsearchIndexService.indexExists("products_v202401011200");

    assertThat(exists).isTrue();
    verify(indicesClient).exists(any(ExistsRequest.class));
  }

  @Test
  @DisplayName("인덱스 삭제 - 존재하는 경우")
  void deleteIndexIfExists() throws Exception {
    BooleanResponse trueResponse = mock(BooleanResponse.class);
    when(trueResponse.value()).thenReturn(true);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(trueResponse);

    DeleteIndexResponse deleteResponse = mock(DeleteIndexResponse.class);
    when(deleteResponse.acknowledged()).thenReturn(true);
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
  @DisplayName("인덱스 생성 중 예외 발생")
  void createIndexException() throws Exception {
    when(mappingService.loadProductMapping()).thenReturn("{\"properties\":{}}");
    when(settingsService.createProductIndexSettings(any(), any()))
        .thenReturn("{\"number_of_shards\":1}");

    BooleanResponse falseResponse = mock(BooleanResponse.class);
    when(falseResponse.value()).thenReturn(false);
    when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(falseResponse);
    when(indicesClient.create(any(CreateIndexRequest.class)))
        .thenThrow(new RuntimeException("Elasticsearch 연결 실패"));

    assertThatThrownBy(() -> elasticsearchIndexService.createNewIndex(indexingContext))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("인덱스 생성 중 에러");
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
}
