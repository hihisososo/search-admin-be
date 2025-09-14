package com.yjlee.search.test.config;

import com.yjlee.search.deployment.service.EC2DeploymentService;
import com.yjlee.search.test.mock.TestDockerDeploymentService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestAnalysisConfig {

  @Bean
  @Primary
  public EC2DeploymentService testEC2DeploymentService() {
    return new TestDockerDeploymentService();
  }
}
