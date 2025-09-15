package com.yjlee.search.test.base;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.ssm.SsmClient;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BaseIntegrationTest.TestConfig.class)
public abstract class BaseIntegrationTest {

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected ElasticsearchClient elasticsearchClient;

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

  protected void deleteAllTestIndices() throws Exception {
    try {
      GetIndexRequest getRequest = GetIndexRequest.of(i -> i.index("*"));
      GetIndexResponse getResponse = elasticsearchClient.indices().get(getRequest);

      getResponse
          .result()
          .keySet()
          .forEach(
              indexName -> {
                if (!indexName.startsWith(".")) {
                  try {
                    DeleteIndexRequest deleteRequest =
                        DeleteIndexRequest.of(d -> d.index(indexName));
                    elasticsearchClient.indices().delete(deleteRequest);
                  } catch (Exception e) {
                  }
                }
              });
    } catch (Exception e) {
    }
  }

  @TestConfiguration
  public static class TestConfig {

    @Bean
    @Primary
    public SsmClient ssmClient() {
      return Mockito.mock(SsmClient.class);
    }
  }
}
