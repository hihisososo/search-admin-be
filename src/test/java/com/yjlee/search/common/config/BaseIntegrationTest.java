package com.yjlee.search.common.config;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
public abstract class BaseIntegrationTest {

  @Container
  protected static ElasticsearchContainer elasticsearchContainer =
      new ElasticsearchContainer(
              DockerImageName.parse("hihisososo/elasticsearch-nori:8.18.3")
                  .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
          .withEnv("xpack.security.enabled", "false")
          .withEnv("discovery.type", "single-node")
          .withEnv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")
          .withExposedPorts(9200)
          .withFileSystemBind(
              new java.io.File("src/test/resources/test-dictionaries")
                  .getAbsolutePath()
                  .replace("\\", "/"),
              "/usr/share/elasticsearch/config/analysis",
              org.testcontainers.containers.BindMode.READ_ONLY)
          .withReuse(true);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.elasticsearch.host", elasticsearchContainer::getHost);
    registry.add("app.elasticsearch.port", () -> elasticsearchContainer.getMappedPort(9200));
  }
}
