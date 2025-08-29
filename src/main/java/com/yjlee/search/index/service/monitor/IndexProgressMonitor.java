package com.yjlee.search.index.service.monitor;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IndexProgressMonitor {

  private final AtomicLong totalProducts = new AtomicLong(0);
  private final AtomicLong indexedProducts = new AtomicLong(0);
  private final AtomicInteger completedBatches = new AtomicInteger(0);
  private final AtomicInteger failedBatches = new AtomicInteger(0);

  private Instant startTime;
  private Instant endTime;

  private Consumer<ProgressUpdate> progressCallback;

  public void start(long totalCount) {
    log.info("색인 진행 모니터링 시작: {}개 상품", totalCount);
    this.totalProducts.set(totalCount);
    this.indexedProducts.set(0);
    this.completedBatches.set(0);
    this.failedBatches.set(0);
    this.startTime = Instant.now();
    this.endTime = null;
  }

  public void updateProgress(int indexedCount) {
    if (indexedCount > 0) {
      completedBatches.incrementAndGet();
      long indexed = indexedProducts.addAndGet(indexedCount);

      double percentage = (indexed * 100.0) / totalProducts.get();
      Duration elapsed = Duration.between(startTime, Instant.now());
      double rate = indexed / (elapsed.toSeconds() + 1.0);

      log.info(
          "진행률: {}/{} ({:.1f}%) - 속도: {:.0f} 문서/초", indexed, totalProducts.get(), percentage, rate);

      if (progressCallback != null) {
        progressCallback.accept(
            new ProgressUpdate(indexed, totalProducts.get(), percentage, rate, elapsed));
      }
    } else {
      failedBatches.incrementAndGet();
      log.error("배치 처리 실패");
    }
  }

  public void complete() {
    this.endTime = Instant.now();
    Duration totalDuration = Duration.between(startTime, endTime);

    double avgRate = indexedProducts.get() / (totalDuration.toSeconds() + 1.0);

    log.info("색인 완료: {}초 소요", totalDuration.toSeconds());
    log.info("총 색인: {}/{}", indexedProducts.get(), totalProducts.get());
    log.info("성공 배치: {}", completedBatches.get());
    log.info("실패 배치: {}", failedBatches.get());
    log.info("평균 속도: {:.0f} 문서/초", avgRate);
  }

  public void setProgressCallback(Consumer<ProgressUpdate> callback) {
    this.progressCallback = callback;
  }

  public IndexingStatistics getStatistics() {
    Duration elapsed =
        endTime != null
            ? Duration.between(startTime, endTime)
            : Duration.between(startTime, Instant.now());

    return new IndexingStatistics(
        totalProducts.get(),
        indexedProducts.get(),
        completedBatches.get(),
        failedBatches.get(),
        elapsed,
        calculateAverageRate());
  }

  private double calculateAverageRate() {
    if (startTime == null) return 0;

    Duration elapsed =
        endTime != null
            ? Duration.between(startTime, endTime)
            : Duration.between(startTime, Instant.now());

    long seconds = Math.max(1, elapsed.toSeconds());
    return (double) indexedProducts.get() / seconds;
  }

  @Getter
  public static class ProgressUpdate {
    private final long indexed;
    private final long total;
    private final double percentage;
    private final double rate;
    private final Duration elapsed;

    public ProgressUpdate(
        long indexed, long total, double percentage, double rate, Duration elapsed) {
      this.indexed = indexed;
      this.total = total;
      this.percentage = percentage;
      this.rate = rate;
      this.elapsed = elapsed;
    }
  }

  @Getter
  public static class IndexingStatistics {
    private final long totalProducts;
    private final long indexedProducts;
    private final int successfulBatches;
    private final int failedBatches;
    private final Duration totalDuration;
    private final double averageRate;

    public IndexingStatistics(
        long totalProducts,
        long indexedProducts,
        int successfulBatches,
        int failedBatches,
        Duration totalDuration,
        double averageRate) {
      this.totalProducts = totalProducts;
      this.indexedProducts = indexedProducts;
      this.successfulBatches = successfulBatches;
      this.failedBatches = failedBatches;
      this.totalDuration = totalDuration;
      this.averageRate = averageRate;
    }

    public double getSuccessRate() {
      int totalBatches = successfulBatches + failedBatches;
      return totalBatches > 0 ? (successfulBatches * 100.0) / totalBatches : 0;
    }
  }
}
