package com.yjlee.search.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "deploymentTaskExecutor")
  public Executor deploymentTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setThreadNamePrefix("deployment-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "evaluationTaskExecutor")
  public Executor evaluationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("evaluation-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "indexingTaskExecutor")
  public Executor indexingTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setThreadNamePrefix("indexing-");
    executor.initialize();
    return executor;
  }
}
