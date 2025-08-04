package com.yjlee.search.common.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.ssm.SsmClient;

@TestConfiguration
@Profile("test")
public class TestAwsConfig {

  @Bean
  @Primary
  public SsmClient ssmClient() {
    return Mockito.mock(SsmClient.class);
  }

}
