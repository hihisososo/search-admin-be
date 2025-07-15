package com.yjlee.search.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class SwaggerConfig {

  @Bean
  public OpenAPI openAPI() {
    log.info("Initializing Swagger OpenAPI documentation");
    return new OpenAPI()
        .info(
            new Info()
                .title("검색 색인 관리 API")
                .version("v1.0")
                .description("Elasticsearch 기반 검색 색인 관리 시스템 API"));
  }
}
