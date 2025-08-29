package com.yjlee.search.index.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class IndexingExecutorConfig {

  private static final int CORE_POOL_SIZE = 4;
  private static final int MAX_POOL_SIZE = 8;
  private static final int QUEUE_CAPACITY = 100;
  private static final long KEEP_ALIVE_TIME = 60L;

  @Bean(name = "indexingExecutor", destroyMethod = "shutdown")
  public ExecutorService indexingExecutor() {
    log.info("색인 실행기 생성: 코어={}, 최대={}, 큐={}", CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);

    return new ThreadPoolExecutor(
        CORE_POOL_SIZE,
        MAX_POOL_SIZE,
        KEEP_ALIVE_TIME,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(QUEUE_CAPACITY),
        new IndexingThreadFactory(),
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  @Bean(name = "embeddingExecutor", destroyMethod = "shutdown")
  public ExecutorService embeddingExecutor() {
    log.info("임베딩 실행기 생성: 코어={}, 최대={}", 2, 4);

    return new ThreadPoolExecutor(
        2,
        4,
        KEEP_ALIVE_TIME,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(50),
        new EmbeddingThreadFactory(),
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  private static class IndexingThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix = "indexing-thread-";

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
      thread.setDaemon(false);
      thread.setPriority(Thread.NORM_PRIORITY);

      thread.setUncaughtExceptionHandler((t, e) -> log.error("스레드 {} 미처리 예외", t.getName(), e));

      return thread;
    }
  }

  private static class EmbeddingThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix = "embedding-thread-";

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
      thread.setDaemon(false);
      thread.setPriority(Thread.NORM_PRIORITY);

      thread.setUncaughtExceptionHandler((t, e) -> log.error("스레드 {} 미처리 예외", t.getName(), e));

      return thread;
    }
  }
}
