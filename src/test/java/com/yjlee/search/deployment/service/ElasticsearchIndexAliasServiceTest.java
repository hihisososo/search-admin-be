package com.yjlee.search.deployment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchIndexAliasServiceTest {

  @Mock private ElasticsearchClient elasticsearchClient;

  private ElasticsearchIndexAliasService aliasService;

  @BeforeEach
  void setUp() {
    aliasService = new ElasticsearchIndexAliasService(elasticsearchClient);
  }

  @Test
  @DisplayName("null 인덱스 이름 검증")
  void updateAliases_WithNullIndexName() {
    assertThatThrownBy(
            () ->
                aliasService.updateAliases(
                    null, "products-search", "products_ac_v202401011200", "autocomplete-search"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("인덱스 이름이 비어있습니다");
  }

  @Test
  @DisplayName("빈 인덱스 이름 검증")
  void updateAliases_WithEmptyIndexName() {
    assertThatThrownBy(
            () ->
                aliasService.updateAliases(
                    "  ", "products-search", "products_ac_v202401011200", "autocomplete-search"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("인덱스 이름이 비어있습니다");
  }

  @Test
  @DisplayName("정상적인 alias 업데이트 - 기본 케이스")
  void updateAliases_Success() throws IOException {
    String productIndexName = "products_v202401011200";
    String productAliasName = "products-search";
    String autocompleteIndexName = "products_ac_v202401011200";
    String autocompleteAliasName = "autocomplete-search";

    var indicesClient = mock(ElasticsearchIndicesClient.class);
    when(elasticsearchClient.indices()).thenReturn(indicesClient);

    var getAliasResponse = mock(co.elastic.clients.elasticsearch.indices.GetAliasResponse.class);
    when(getAliasResponse.result()).thenReturn(new java.util.HashMap<>());
    when(indicesClient.getAlias(
            any(co.elastic.clients.elasticsearch.indices.GetAliasRequest.class)))
        .thenReturn(getAliasResponse);

    UpdateAliasesResponse updateResponse = mock(UpdateAliasesResponse.class);
    when(indicesClient.updateAliases(any(UpdateAliasesRequest.class))).thenReturn(updateResponse);

    assertThatCode(
            () ->
                aliasService.updateAliases(
                    productIndexName,
                    productAliasName,
                    autocompleteIndexName,
                    autocompleteAliasName))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("alias 업데이트 중 IOException 발생")
  void updateAliases_ThrowsIOException() throws IOException {
    String productIndexName = "products_v202401011200";
    String productAliasName = "products-search";
    String autocompleteIndexName = "products_ac_v202401011200";
    String autocompleteAliasName = "autocomplete-search";

    var indicesClient = mock(ElasticsearchIndicesClient.class);
    when(elasticsearchClient.indices()).thenReturn(indicesClient);

    var getAliasResponse = mock(co.elastic.clients.elasticsearch.indices.GetAliasResponse.class);
    when(getAliasResponse.result()).thenReturn(new java.util.HashMap<>());
    when(indicesClient.getAlias(
            any(co.elastic.clients.elasticsearch.indices.GetAliasRequest.class)))
        .thenReturn(getAliasResponse);

    when(indicesClient.updateAliases(any(UpdateAliasesRequest.class)))
        .thenThrow(new IOException("Connection failed"));

    assertThatThrownBy(
            () ->
                aliasService.updateAliases(
                    productIndexName,
                    productAliasName,
                    autocompleteIndexName,
                    autocompleteAliasName))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Elasticsearch alias 변경 실패")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  @DisplayName("단일 alias 업데이트")
  void updateAlias_SingleIndex() throws IOException {
    String indexName = "products_v202401011200";
    String aliasName = "products-search";

    var indicesClient = mock(ElasticsearchIndicesClient.class);
    when(elasticsearchClient.indices()).thenReturn(indicesClient);

    var getAliasResponse = mock(co.elastic.clients.elasticsearch.indices.GetAliasResponse.class);
    when(getAliasResponse.result()).thenReturn(new java.util.HashMap<>());
    when(indicesClient.getAlias(
            any(co.elastic.clients.elasticsearch.indices.GetAliasRequest.class)))
        .thenReturn(getAliasResponse);

    UpdateAliasesResponse updateResponse = mock(UpdateAliasesResponse.class);
    when(indicesClient.updateAliases(any(UpdateAliasesRequest.class))).thenReturn(updateResponse);

    assertThatCode(() -> aliasService.updateAlias(indexName, aliasName)).doesNotThrowAnyException();
  }
}
