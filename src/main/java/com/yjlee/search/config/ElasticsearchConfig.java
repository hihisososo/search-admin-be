package com.yjlee.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ElasticsearchConfig {

  private final ElasticsearchProperties elasticsearchProperties;

  @Bean
  public ElasticsearchClient elasticsearchClient() {
    log.info(
        "Connecting to Elasticsearch at {}://{}:{}",
        elasticsearchProperties.getScheme(),
        elasticsearchProperties.getHost(),
        elasticsearchProperties.getPort());

    RestClient restClient =
        RestClient.builder(
                new HttpHost(
                    elasticsearchProperties.getHost(),
                    elasticsearchProperties.getPort(),
                    elasticsearchProperties.getScheme()))
            .build();

    RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    return new ElasticsearchClient(transport);
  }

  @Getter
  @Setter
  @ConfigurationProperties(prefix = "app.elasticsearch")
  @Configuration
  public static class ElasticsearchProperties {
    private String host;
    private int port;
    private String scheme = "http";
  }
}
