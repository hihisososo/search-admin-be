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
    executor.setCorePoolSize(8);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("llm-async-");
    executor.setKeepAliveSeconds(60);
    executor.initialize();
    return executor;
  }

  @Bean(name = "evaluationTaskExecutor")
  public Executor evaluationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(6);
    executor.setMaxPoolSize(12);
    executor.setQueueCapacity(150);
    executor.setThreadNamePrefix("evaluation-async-");
    executor.setKeepAliveSeconds(60);
    // 큐가 가득 찰 때 호출자 스레드에서 실행하여 백프레셔 제공
    executor.setRejectedExecutionHandler(
        new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
