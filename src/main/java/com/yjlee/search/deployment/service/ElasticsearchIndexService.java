package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.PutAliasRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexService {

  private final ElasticsearchClient elasticsearchClient;
  private final ResourceLoader resourceLoader;

  private static final String PRODUCTS_SEARCH_ALIAS = "products-search";

  public String createNewIndex(String version) throws IOException {
    String indexName = version;

    if (indexExists(indexName)) {
      deleteIndex(indexName);
    }

    String mappingJson = createProductIndexMapping();
    String settingsJson = createProductIndexSettings(version);

    CreateIndexRequest request =
        CreateIndexRequest.of(
            i ->
                i.index(indexName)
                    .mappings(
                        TypeMapping.of(
                            m ->
                                m.withJson(
                                    new java.io.ByteArrayInputStream(mappingJson.getBytes()))))
                    .settings(
                        IndexSettings.of(
                            s ->
                                s.withJson(
                                    new java.io.ByteArrayInputStream(settingsJson.getBytes())))));

    elasticsearchClient.indices().create(request);
    log.info("새 인덱스 생성 완료: {}", indexName);

    return indexName;
  }

  public void deleteIndexIfExists(String indexName) throws IOException {
    if (indexName != null && indexExists(indexName)) {
      deleteIndex(indexName);
    }
  }

  public void updateProductsSearchAlias(String newIndexName) throws IOException {
    PutAliasRequest request =
        PutAliasRequest.of(a -> a.index(newIndexName).name(PRODUCTS_SEARCH_ALIAS));
    elasticsearchClient.indices().putAlias(request);
    log.info("products-search alias 업데이트 완료: {}", newIndexName);
  }

  public boolean indexExists(String indexName) throws IOException {
    ExistsRequest request = ExistsRequest.of(e -> e.index(indexName));
    return elasticsearchClient.indices().exists(request).value();
  }

  private void deleteIndex(String indexName) throws IOException {
    DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
    elasticsearchClient.indices().delete(request);
    log.info("인덱스 삭제 완료: {}", indexName);
  }

  private String createProductIndexMapping() throws IOException {
    try {
      var resource = resourceLoader.getResource("classpath:elasticsearch/product-mapping.json");
      return StreamUtils.copyToString(
          resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("상품 매핑 파일 읽기 실패", e);
      throw new IOException("상품 매핑 파일을 읽을 수 없습니다.", e);
    }
  }

  private String createProductIndexSettings(String version) throws IOException {
    try {
      var resource = resourceLoader.getResource("classpath:elasticsearch/product-settings.json");
      String settingsTemplate =
          StreamUtils.copyToString(
              resource.getInputStream(), java.nio.charset.StandardCharsets.UTF_8);

      String userDictPath = "/usr/share/elasticsearch/config/analysis/user/" + version + ".txt";
      return settingsTemplate.replace("{USER_DICT_PATH}", userDictPath);
    } catch (Exception e) {
      log.error("상품 설정 파일 읽기 실패", e);
      throw new IOException("상품 설정 파일을 읽을 수 없습니다.", e);
    }
  }
}
