package com.yjlee.search.evaluation.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMRateLimitManager {

  private final LLMService llmService;
  private final AtomicBoolean isRateLimited = new AtomicBoolean(false);
  private ScheduledExecutorService scheduler;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Lock lock = new ReentrantLock();

  public void startHealthCheck() {
    lock.lock();
    try {
      if (running.get()) {
        log.debug("Health check 이미 실행 중");
        return;
      }

      running.set(true);
      scheduler = Executors.newScheduledThreadPool(1);

      // 초기 지연 없이 5초마다 실행
      scheduler.scheduleWithFixedDelay(this::performHealthCheck, 0, 5, TimeUnit.SECONDS);
      log.info("LLM health check 스케줄러 시작");
    } finally {
      lock.unlock();
    }
  }

  public void stopHealthCheck() {
    lock.lock();
    try {
      if (!running.get()) {
        log.debug("Health check 이미 중지됨");
        return;
      }

      running.set(false);
      if (scheduler != null) {
        scheduler.shutdown();
        try {
          if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
          }
        } catch (InterruptedException e) {
          scheduler.shutdownNow();
          Thread.currentThread().interrupt();
        }
        scheduler = null;
      }
      log.info("LLM health check 스케줄러 종료");
    } finally {
      lock.unlock();
    }
  }

  private void performHealthCheck() {
    try {
      if (isRateLimited.get()) {
        // Rate limit 상태일 때만 health check 수행
        if (llmService.isHealthy()) {
          clearRateLimit();
          log.info("Health check 성공 - Rate limit 해제됨");
        } else {
          log.debug("Health check 실패 - Rate limit 여전히 활성");
        }
      }
    } catch (Exception e) {
      log.error("Health check 오류", e);
    }
  }

  public void setRateLimitActive() {
    isRateLimited.getAndSet(true);
    log.warn("Rate limit 활성화 - Health check로 해제 확인 예정");
  }

  public void clearRateLimit() {
    isRateLimited.getAndSet(false);
    log.info("Rate limit 해제됨");
  }

  public boolean isRateLimited() {
    return isRateLimited.get();
  }

  public void waitIfRateLimited() throws InterruptedException {
    while (isRateLimited()) {
      log.debug("Rate limit 대기 중... Health check가 해제 확인 중");
      TimeUnit.SECONDS.sleep(1);
    }
  }

  @PreDestroy
  public void shutdown() {
    stopHealthCheck();
  }
}
