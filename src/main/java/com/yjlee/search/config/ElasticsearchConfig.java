package com.yjlee.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
  public JacksonJsonpMapper jsonpMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return new JacksonJsonpMapper(objectMapper);
  }

  @Bean
  public RestClient restClient() {
    String host = elasticsearchProperties.getHost();
    int port = elasticsearchProperties.getPort();
    String scheme = elasticsearchProperties.getScheme();

    HttpHost httpHost = new HttpHost(host, port, scheme);
    return RestClient.builder(httpHost).build();
  }

  @Bean
  public ElasticsearchClient elasticsearchClient(
      RestClient restClient, JacksonJsonpMapper jsonpMapper) {
    RestClientTransport transport = new RestClientTransport(restClient, jsonpMapper);
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
