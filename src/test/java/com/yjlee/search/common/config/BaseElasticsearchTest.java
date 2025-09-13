package com.yjlee.search.common.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class BaseElasticsearchTest {

  protected static final String ELASTICSEARCH_VERSION = "8.18.3";

  @Container
  protected static ElasticsearchContainer elasticsearchContainer =
      new ElasticsearchContainer(
              org.testcontainers.utility.DockerImageName.parse(
                      "hihisososo/elasticsearch-nori:" + ELASTICSEARCH_VERSION)
                  .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
          .withEnv("discovery.type", "single-node")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

  protected static RestClient restClient;
  protected static ElasticsearchClient elasticsearchClient;

  @BeforeAll
  static void setUp() {
    restClient =
        RestClient.builder(HttpHost.create(elasticsearchContainer.getHttpHostAddress())).build();

    ElasticsearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper());

    elasticsearchClient = new ElasticsearchClient(transport);
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (restClient != null) {
      restClient.close();
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.elasticsearch.uris", elasticsearchContainer::getHttpHostAddress);
  }

  protected void createIndex(String indexName, String settings, String mappings) throws Exception {
    elasticsearchClient
        .indices()
        .create(
            c ->
                c.index(indexName)
                    .settings(
                        s -> s.withJson(new java.io.ByteArrayInputStream(settings.getBytes())))
                    .mappings(
                        m -> m.withJson(new java.io.ByteArrayInputStream(mappings.getBytes()))));
  }

  protected void deleteIndex(String indexName) throws Exception {
    if (elasticsearchClient.indices().exists(e -> e.index(indexName)).value()) {
      elasticsearchClient.indices().delete(d -> d.index(indexName));
    }
  }
}
