package com.yjlee.search.evaluation.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMQueueManager {

  private final LLMService llmService;
  private final LLMRateLimitManager rateLimitManager;

  @Value("${llm.queue.worker-threads:5}")
  private int workerThreads;

  private final BlockingQueue<LLMTask<?>> taskQueue = new LinkedBlockingQueue<>();
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final AtomicInteger activeWorkers = new AtomicInteger(0);
  private ExecutorService executorService;

  @PostConstruct
  public void init() {
    executorService =
        Executors.newFixedThreadPool(
            workerThreads,
            r -> {
              Thread thread = new Thread(r);
              thread.setName("llm-worker-" + thread.getId());
              thread.setDaemon(true);
              return thread;
            });

    for (int i = 0; i < workerThreads; i++) {
      final int workerId = i;
      executorService.submit(() -> workerLoop(workerId));
    }
    log.info("LLM Queue Manager 시작 - Worker 스레드: {}개", workerThreads);
  }

  @PreDestroy
  public void shutdown() {
    log.info("LLM Queue Manager 종료 중...");
    running.set(false);

    if (executorService != null) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    log.info("LLM Queue Manager 종료 완료");
  }

  private void workerLoop(int workerId) {
    log.debug("LLM Worker {} 시작", workerId);
    while (running.get()) {
      try {
        boolean isLimited = rateLimitManager.isRateLimited();
        if (isLimited) {
          rateLimitManager.waitIfRateLimited();
          continue;
        }

        LLMTask<?> task = taskQueue.poll(1, TimeUnit.SECONDS);
        if (task == null) {
          continue;
        }

        activeWorkers.incrementAndGet();
        log.debug("Worker {} - LLM 작업 처리 시작: {}", workerId, task.description);

        try {
          String response = llmService.callLLMAPI(task.prompt, task.temperature);

          processTaskResult(task, response);
          log.debug("Worker {} - LLM 작업 완료: {}", workerId, task.description);

        } catch (Exception e) {
          if (e.getMessage() != null && e.getMessage().contains("429")) {
            log.warn("Rate limit 감지 - 모든 작업 중단");
            rateLimitManager.setRateLimitActive();
            try {
              taskQueue.put(task);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            }
          } else {
            log.error("LLM 작업 실패: {}", task.description, e);
            task.future.completeExceptionally(e);
          }
        }

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.error("Worker {} - 예상치 못한 오류", workerId, e);
      } finally {
        activeWorkers.decrementAndGet();
      }
    }
    log.debug("LLM Worker {} 종료", workerId);
  }

  public <T> CompletableFuture<T> submitTask(
      String prompt,
      Double temperature,
      Function<String, T> responseProcessor,
      String description) {

    CompletableFuture<T> future = new CompletableFuture<>();
    LLMTask<T> task = new LLMTask<>(prompt, temperature, responseProcessor, future, description);

    try {
      taskQueue.put(task);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.completeExceptionally(e);
    }

    return future;
  }

  public CompletableFuture<String> submitSimpleTask(String prompt, String description) {
    return submitTask(prompt, null, response -> response, description);
  }

  @SuppressWarnings("unchecked")
  private <T> void processTaskResult(LLMTask<?> task, String response) {
    LLMTask<T> typedTask = (LLMTask<T>) task;
    T result = typedTask.responseProcessor.apply(response);
    typedTask.future.complete(result);
  }

  public void startHealthCheck() {
    rateLimitManager.startHealthCheck();
  }

  public void stopHealthCheck() {
    rateLimitManager.stopHealthCheck();
  }

  public int getQueueSize() {
    return taskQueue.size();
  }

  public int getActiveWorkers() {
    return activeWorkers.get();
  }

  @RequiredArgsConstructor
  private static class LLMTask<T> {
    final String prompt;
    final Double temperature;
    final Function<String, T> responseProcessor;
    final CompletableFuture<T> future;
    final String description;
  }
}
