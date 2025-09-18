package com.yjlee.search.deployment.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import java.io.ByteArrayInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexService {

  private final ElasticsearchClient elasticsearchClient;

  public void createIndex(String indexName, String mappingJson, String settingsJson) {
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
      log.info("인덱스 생성 완료: {}", indexName);
    } catch (Exception e) {
      throw new RuntimeException("인덱스 생성 중 에러: " + indexName, e);
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
      log.info("인덱스 삭제 완료: {}", indexName);
    } catch (Exception e) {
      throw new RuntimeException("ES 연결 중 에러");
    }
  }
}