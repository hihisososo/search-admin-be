package com.yjlee.search.evaluation.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMRateLimitManager {

  private final LLMService llmService;
  private final AtomicBoolean isRateLimited = new AtomicBoolean(false);
  private Thread healthCheckThread;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public synchronized void startHealthCheck() {
    if (running.get()) {
      log.debug("Health check 이미 실행 중");
      return;
    }

    running.set(true);
    healthCheckThread = new Thread(this::healthCheckLoop, "llm-health-check");
    healthCheckThread.setDaemon(true);
    healthCheckThread.start();
    log.info("LLM health check 스레드 시작");
  }

  public synchronized void stopHealthCheck() {
    if (!running.get()) {
      log.debug("Health check 이미 중지됨");
      return;
    }

    running.set(false);
    if (healthCheckThread != null) {
      healthCheckThread.interrupt();
      try {
        healthCheckThread.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      healthCheckThread = null;
    }
    log.info("LLM health check 스레드 종료");
  }

  private void healthCheckLoop() {
    while (running.get()) {
      try {
        if (isRateLimited.get()) {
          // Rate limit 상태일 때만 health check 수행
          Thread.sleep(5000); // 5초 대기

          if (llmService.isHealthy()) {
            clearRateLimit();
            log.info("✅ Health check 성공 - Rate limit 해제됨");
          } else {
            log.debug("Health check 실패 - Rate limit 여전히 활성");
          }
        } else {
          // Rate limit 없을 때는 30초 대기
          Thread.sleep(30000);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.error("Health check 스레드 오류", e);
      }
    }
  }

  public void setRateLimitActive() {
    boolean wasLimited = isRateLimited.getAndSet(true);
    log.warn("[DEBUG] Rate limit 활성화 - 이전 상태: {}, 현재: true", wasLimited);
    log.warn("⚠️ Rate limit 활성화 - Health check로 해제 확인 예정");
  }

  public void clearRateLimit() {
    boolean wasLimited = isRateLimited.getAndSet(false);
    log.info("[DEBUG] Rate limit 해제 - 이전 상태: {}, 현재: false", wasLimited);
    log.info("✅ Rate limit 해제됨");
  }

  public boolean isRateLimited() {
    boolean limited = isRateLimited.get();
    if (limited) {
      log.trace("[DEBUG] Rate limit 상태 확인: 활성화됨");
    }
    return limited;
  }

  public void waitIfRateLimited() throws InterruptedException {
    while (isRateLimited()) {
      log.debug("Rate limit 대기 중... Health check가 해제 확인 중");
      Thread.sleep(1000); // 1초씩 대기
    }
  }

  @PreDestroy
  public void shutdown() {
    stopHealthCheck();
  }
}
