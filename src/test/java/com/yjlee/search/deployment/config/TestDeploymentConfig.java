package com.yjlee.search.deployment.config;

import com.yjlee.search.deployment.service.SsmCommandService;
import com.yjlee.search.deployment.service.TestSsmCommandService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestDeploymentConfig {

  @Bean
  @Primary
  public SsmCommandService ssmCommandService() {
    return new TestSsmCommandService();
  }
}
