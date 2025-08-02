package com.yjlee.search.common.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class TestContainerConfig {

  private static ElasticsearchContainer elasticsearchContainer = null;

  @Bean
  public ElasticsearchContainer elasticsearchContainer() {
    if (elasticsearchContainer == null) {
      elasticsearchContainer =
          new ElasticsearchContainer(
                  DockerImageName.parse("hihisososo/elasticsearch-nori:8.18.3")
                      .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
              .withEnv("xpack.security.enabled", "false")
              .withEnv("discovery.type", "single-node")
              .withEnv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")
              .withReuse(true);

      elasticsearchContainer.start();
    }
    return elasticsearchContainer;
  }
}
