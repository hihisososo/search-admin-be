package com.yjlee.search.common.service;

import com.yjlee.search.evaluation.service.LLMRateLimitManager;
import com.yjlee.search.evaluation.service.LLMService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 모든 LLM API 호출을 관리하는 범용 큐 매니저 Rate Limit 처리와 재시도 로직을 중앙화 */
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
  private Thread[] workers;

  @PostConstruct
  public void init() {
    workers = new Thread[workerThreads];
    for (int i = 0; i < workerThreads; i++) {
      final int workerId = i;
      workers[i] = new Thread(() -> workerLoop(workerId), "llm-queue-worker-" + i);
      workers[i].setDaemon(true);
      workers[i].start();
    }
    log.info("LLM Queue Manager 시작 - Worker 스레드: {}개", workerThreads);
  }

  @PreDestroy
  public void shutdown() {
    log.info("LLM Queue Manager 종료 중...");
    running.set(false);
    for (Thread worker : workers) {
      worker.interrupt();
    }
    for (Thread worker : workers) {
      try {
        worker.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    log.info("LLM Queue Manager 종료 완료");
  }

  private void workerLoop(int workerId) {
    log.debug("LLM Worker {} 시작", workerId);
    while (running.get()) {
      try {
        // Rate limit 체크
        if (rateLimitManager.isRateLimited()) {
          rateLimitManager.waitIfRateLimited();
          continue;
        }

        // 큐에서 작업 가져오기
        LLMTask<?> task = taskQueue.poll(1, TimeUnit.SECONDS);
        if (task == null) {
          continue;
        }

        activeWorkers.incrementAndGet();
        log.debug("Worker {} - LLM 작업 처리 시작: {}", workerId, task.description);

        try {
          // LLM API 호출
          String response = llmService.callLLMAPI(task.prompt, task.temperature);

          // 결과 처리 및 완료
          processTaskResult(task, response);

          log.debug("Worker {} - LLM 작업 완료: {}", workerId, task.description);

        } catch (Exception e) {
          if (e.getMessage() != null && e.getMessage().contains("429")) {
            log.warn("Rate limit 감지 - 모든 작업 중단");
            rateLimitManager.setRateLimitActive();
            // 실패한 작업 다시 큐에 넣기
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

  /** LLM API 호출을 큐에 추가하고 CompletableFuture 반환 */
  public <T> CompletableFuture<T> submitTask(
      String prompt,
      Double temperature,
      Function<String, T> responseProcessor,
      String description) {

    CompletableFuture<T> future = new CompletableFuture<>();
    LLMTask<T> task = new LLMTask<>(prompt, temperature, responseProcessor, future, description);

    try {
      taskQueue.put(task);
      log.debug("LLM 작업 큐에 추가: {}", description);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.completeExceptionally(e);
    }

    return future;
  }

  /** 간단한 텍스트 응답을 위한 편의 메서드 */
  public CompletableFuture<String> submitSimpleTask(String prompt, String description) {
    return submitTask(prompt, null, response -> response, description);
  }

  /** 타입 안전한 결과 처리 메서드 */
  @SuppressWarnings("unchecked")
  private <T> void processTaskResult(LLMTask<?> task, String response) {
    LLMTask<T> typedTask = (LLMTask<T>) task;
    T result = typedTask.responseProcessor.apply(response);
    typedTask.future.complete(result);
  }

  /** Health check 시작 (평가 작업 시작 시) */
  public void startHealthCheck() {
    rateLimitManager.startHealthCheck();
  }

  /** Health check 중지 (평가 작업 완료 시) */
  public void stopHealthCheck() {
    rateLimitManager.stopHealthCheck();
  }

  /** 큐 상태 조회 */
  public int getQueueSize() {
    return taskQueue.size();
  }

  public int getActiveWorkers() {
    return activeWorkers.get();
  }

  /** LLM 작업 데이터 클래스 */
  private static class LLMTask<T> {
    final String prompt;
    final Double temperature;
    final Function<String, T> responseProcessor;
    final CompletableFuture<T> future;
    final String description;

    LLMTask(
        String prompt,
        Double temperature,
        Function<String, T> responseProcessor,
        CompletableFuture<T> future,
        String description) {
      this.prompt = prompt;
      this.temperature = temperature;
      this.responseProcessor = responseProcessor;
      this.future = future;
      this.description = description;
    }
  }
}
