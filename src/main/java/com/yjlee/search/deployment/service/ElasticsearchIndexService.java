package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.yjlee.search.deployment.domain.IndexingContext;
import java.io.ByteArrayInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexService {

  private final ElasticsearchClient elasticsearchClient;
  private final ElasticsearchSettingsService settingsService;
  private final ElasticsearchMappingService mappingService;

  public String createNewIndex(IndexingContext context) {
    String version = context.getVersion();
    String productIndexName = context.getProductIndexName();
    String autocompleteIndexName = context.getAutocompleteIndexName();
    String synonymSetName = context.getSynonymSetName();

    createProductIndex(productIndexName, version, synonymSetName);
    createAutocompleteIndex(autocompleteIndexName, version);

    return productIndexName;
  }

  private void createProductIndex(String indexName, String version, String synonymSetName) {
    deleteIndexIfExists(indexName);

    String mappingJson = mappingService.loadProductMapping();
    String settingsJson = settingsService.createProductIndexSettings(version, synonymSetName);

    createIndex(indexName, mappingJson, settingsJson);
  }

  private void createAutocompleteIndex(String indexName, String version) {
    deleteIndexIfExists(indexName);

    String mappingJson = mappingService.loadAutocompleteMapping();
    String settingsJson = settingsService.createAutocompleteIndexSettings(version);

    createIndex(indexName, mappingJson, settingsJson);
  }

  private void createIndex(String indexName, String mappingJson, String settingsJson) {
    CreateIndexRequest request =
        CreateIndexRequest.of(
            i ->
                i.index(indexName)
                    .mappings(
                        TypeMapping.of(
                            m -> m.withJson(new ByteArrayInputStream(mappingJson.getBytes()))))
                    .settings(
                        IndexSettings.of(
                            s -> s.withJson(new ByteArrayInputStream(settingsJson.getBytes())))));

    try {
      elasticsearchClient.indices().create(request);
    } catch (Exception e) {
      throw new RuntimeException("인덱스 생성 중 에러", e);
    }
  }

  public void deleteIndexIfExists(String indexName) {
    if (indexExists(indexName)) {
      deleteIndex(indexName);
    }
  }

  public boolean indexExists(String indexName) {
    ExistsRequest request = ExistsRequest.of(e -> e.index(indexName));
    try {
      return elasticsearchClient.indices().exists(request).value();
    } catch (Exception e) {
      throw new RuntimeException("ES 연결 중 에러");
    }
  }

  private void deleteIndex(String indexName) {
    DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
    try {
      elasticsearchClient.indices().delete(request);
    } catch (Exception e) {
      throw new RuntimeException("ES 연결 중 에러");
    }
  }
}
