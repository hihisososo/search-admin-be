package com.yjlee.search.test.base;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.service.CommandService;
import com.yjlee.search.common.service.FileUploadService;
import com.yjlee.search.index.provider.IndexNameProvider;
import com.yjlee.search.test.mock.TestDockerFileUploadService;
import com.yjlee.search.test.mock.TestIndexNameProvider;
import com.yjlee.search.test.mock.TestSsmCommandService;
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
    String[] patterns = {TestIndexNameProvider.TEST_PREFIX + "*"};
    for (String pattern : patterns) {
      DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(pattern));
      elasticsearchClient.indices().delete(request);
    }
  }

  @TestConfiguration
  public static class TestConfig {

    @Bean
    @Primary
    public SsmClient ssmClient() {
      return Mockito.mock(SsmClient.class);
    }

    @Bean
    @Primary
    public IndexNameProvider testIndexNameProvider() {
      return new TestIndexNameProvider();
    }

    @Bean
    @Primary
    public CommandService commandService() {
      return new TestSsmCommandService();
    }

    @Bean
    @Primary
    public FileUploadService fileUploadService() {
      return new TestDockerFileUploadService();
    }
  }
}
