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
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("deployment-async-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "llmTaskExecutor")
  public Executor llmTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(12); // 8 → 12로 증가 (더 많은 쿼리 동시 처리)
    executor.setMaxPoolSize(24); // 16 → 24로 증가
    executor.setQueueCapacity(200); // 100 → 200으로 증가
    executor.setThreadNamePrefix("llm-async-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "evaluationTaskExecutor")
  public Executor evaluationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("evaluation-async-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "generalTaskExecutor")
  public Executor generalTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("general-async-");
    executor.initialize();
    return executor;
  }
}
