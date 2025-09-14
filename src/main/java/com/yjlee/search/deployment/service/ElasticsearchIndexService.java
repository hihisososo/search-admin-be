package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.PutAliasRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.index.provider.IndexNameProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexService {

  private static final int HTTP_NOT_FOUND = 404;
  private static final String MAPPING_PATH_PREFIX = "classpath:elasticsearch/";
  private static final String PRODUCT_MAPPING_FILE = "product-mapping.json";
  private static final String PRODUCT_SETTINGS_FILE = "product-settings.json";
  private static final String AUTOCOMPLETE_MAPPING_FILE = "autocomplete-mapping.json";
  private static final String AUTOCOMPLETE_SETTINGS_FILE = "autocomplete-settings.json";

  private final ElasticsearchClient elasticsearchClient;
  private final ResourceLoader resourceLoader;
  private final IndexNameProvider indexNameProvider;

  public String createNewIndex(String version, EnvironmentType environmentType) throws IOException {
    String productIndexName = indexNameProvider.getProductIndexName(version);
    String autocompleteIndexName = indexNameProvider.getAutocompleteIndexName(version);
    String synonymSetName = indexNameProvider.getSynonymSetName(version);

    createProductIndex(productIndexName, version, synonymSetName);
    createAutocompleteIndex(autocompleteIndexName, version);

    return productIndexName;
  }

  private void createProductIndex(String indexName, String version, String synonymSetName)
      throws IOException {
    deleteIndexIfExists(indexName);

    String mappingJson = loadResourceFile(MAPPING_PATH_PREFIX + PRODUCT_MAPPING_FILE);
    String settingsJson = createProductIndexSettings(version, synonymSetName);

    createIndex(indexName, mappingJson, settingsJson);
  }

  private void createAutocompleteIndex(String indexName, String version) throws IOException {
    deleteIndexIfExists(indexName);

    String mappingJson = loadResourceFile(MAPPING_PATH_PREFIX + AUTOCOMPLETE_MAPPING_FILE);
    String settingsJson = createAutocompleteIndexSettings(version);

    createIndex(indexName, mappingJson, settingsJson);
  }

  private void createIndex(String indexName, String mappingJson, String settingsJson)
      throws IOException {
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

    elasticsearchClient.indices().create(request);
  }

  public void deleteIndexIfExists(String indexName) throws IOException {
    if (indexExists(indexName)) {
      deleteIndex(indexName);
    }
  }

  public void updateProductsSearchAlias(String newIndexName) throws IOException {
    updateAlias(newIndexName, indexNameProvider.getProductsSearchAlias());
  }

  public void updateAutocompleteSearchAlias(String newIndexName) throws IOException {
    updateAlias(newIndexName, indexNameProvider.getAutocompleteSearchAlias());
  }

  private void updateAlias(String newIndexName, String aliasName) throws IOException {
    GetAliasRequest getAliasRequest = GetAliasRequest.of(a -> a.name(aliasName));

    try {
      GetAliasResponse aliasResponse = elasticsearchClient.indices().getAlias(getAliasRequest);

      ArrayList<Action> actions = new ArrayList<Action>();

      for (String existingIndex : aliasResponse.result().keySet()) {
        if (!existingIndex.equals(newIndexName)) {
          actions.add(Action.of(a -> a.remove(r -> r.index(existingIndex).alias(aliasName))));
        }
      }

      actions.add(Action.of(a -> a.add(add -> add.index(newIndexName).alias(aliasName))));

      if (!actions.isEmpty()) {
        UpdateAliasesRequest updateRequest = UpdateAliasesRequest.of(u -> u.actions(actions));
        elasticsearchClient.indices().updateAliases(updateRequest);
      }

    } catch (ElasticsearchException e) {
      if (e.response().status() == HTTP_NOT_FOUND) {
        PutAliasRequest putAliasRequest =
            PutAliasRequest.of(a -> a.index(newIndexName).name(aliasName));
        elasticsearchClient.indices().putAlias(putAliasRequest);
      } else {
        throw e;
      }
    }
  }

  public boolean indexExists(String indexName) throws IOException {
    ExistsRequest request = ExistsRequest.of(e -> e.index(indexName));
    return elasticsearchClient.indices().exists(request).value();
  }

  private void deleteIndex(String indexName) throws IOException {
    DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
    elasticsearchClient.indices().delete(request);
  }

  private String loadResourceFile(String path) throws IOException {
    Resource resource = resourceLoader.getResource(path);
    return StreamUtils.copyToString(
        resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
  }

  private String createProductIndexSettings(String version, String synonymSetName)
      throws IOException {
    String settingsTemplate = loadResourceFile(MAPPING_PATH_PREFIX + PRODUCT_SETTINGS_FILE);

    return settingsTemplate
        .replace("{USER_DICT_PATH}", indexNameProvider.getUserDictPath(version))
        .replace("{STOPWORD_DICT_PATH}", indexNameProvider.getStopwordDictPath(version))
        .replace("{UNIT_DICT_PATH}", indexNameProvider.getUnitDictPath(version))
        .replace("{SYNONYM_SET_NAME}", synonymSetName);
  }

  private String createAutocompleteIndexSettings(String version) throws IOException {
    String settingsTemplate = loadResourceFile(MAPPING_PATH_PREFIX + AUTOCOMPLETE_SETTINGS_FILE);
    return settingsTemplate.replace("{USER_DICT_PATH}", indexNameProvider.getUserDictPath(version));
  }
}