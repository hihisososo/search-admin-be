package com.yjlee.search.test.base;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.test.config.TestIndexNameProvider;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected ElasticsearchClient elasticsearchClient;

  @BeforeEach
  void cleanupIndices() throws Exception {
    deleteAllTestIndices();
  }

  protected void createIndex(String indexName, String settings, String mappings) throws Exception {
    CreateIndexRequest request =
        CreateIndexRequest.of(
            i ->
                i.index(indexName)
                    .settings(
                        IndexSettings.of(
                            s -> s.withJson(new ByteArrayInputStream(settings.getBytes()))))
                    .mappings(
                        TypeMapping.of(
                            m -> m.withJson(new ByteArrayInputStream(mappings.getBytes())))));
    elasticsearchClient.indices().create(request);
  }

  protected void deleteIndex(String indexName) throws Exception {
    if (indexExists(indexName)) {
      DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(indexName));
      elasticsearchClient.indices().delete(request);
    }
  }

  protected boolean indexExists(String indexName) throws Exception {
    ExistsRequest request = ExistsRequest.of(e -> e.index(indexName));
    return elasticsearchClient.indices().exists(request).value();
  }

  private void deleteAllTestIndices() throws Exception {
    String[] patterns = {TestIndexNameProvider.TEST_PREFIX + "*"};
    for (String pattern : patterns) {
      try {
        DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(pattern));
        elasticsearchClient.indices().delete(request);
      } catch (Exception e) {
      }
    }
  }
}
