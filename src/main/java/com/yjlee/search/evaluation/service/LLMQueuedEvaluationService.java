package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMQueuedEvaluationService {

  private final LLMRateLimitManager rateLimitManager;
  private final LLMQueryEvaluationWorker evaluationWorker;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final QueryProductMappingRepository queryProductMappingRepository;
  private final LLMBatchProcessor batchProcessor;

  @Value("${evaluation.llm.batch-size:10}")
  private int batchSize;

  @Value("${evaluation.llm.worker-threads:5}")
  private int workerThreads;

  // 평가 작업 큐
  private final ConcurrentLinkedQueue<EvaluationBatch> evaluationQueue =
      new ConcurrentLinkedQueue<>();
  private final AtomicInteger activeWorkers = new AtomicInteger(0);
  private final AtomicInteger totalBatchesAdded = new AtomicInteger(0);
  private final AtomicInteger totalBatchesProcessed = new AtomicInteger(0);

  // 큐 처리 스케줄러 (100ms마다 실행)
  @Scheduled(fixedDelay = 100)
  public void processQueueScheduler() {
    // Rate limit 상태 체크
    if (rateLimitManager.isRateLimited()) {
      return;
    }

    // 동시 실행 워커 수 제한
    if (activeWorkers.get() >= workerThreads) {
      return;
    }

    // 큐에서 배치 가져오기
    EvaluationBatch batch = evaluationQueue.poll();
    if (batch != null) {
      // activeWorkers 증가
      activeWorkers.incrementAndGet();

      // 비동기로 배치 처리
      batchProcessor.processBatchAsync(
          batch.query,
          batch.products,
          batch.mappings,
          batch.evaluationQuery,
          () -> {
            // 성공 콜백
            totalBatchesProcessed.incrementAndGet();
            activeWorkers.decrementAndGet();
          },
          () -> {
            // Rate limit 콜백
            evaluationQueue.offer(batch);
            activeWorkers.decrementAndGet();
          });
    }
  }

  public void evaluateCandidatesForQueries(List<Long> queryIds) {
    evaluateCandidatesForQueries(queryIds, null);
  }

  public void evaluateCandidatesForQueries(List<Long> queryIds, ProgressCallback progressCallback) {
    log.info("큐 기반 LLM 평가 시작: {}개 쿼리", queryIds.size());

    // 카운터 초기화
    totalBatchesAdded.set(0);
    totalBatchesProcessed.set(0);

    // Health check 스레드 시작
    rateLimitManager.startHealthCheck();

    try {
      List<EvaluationQuery> queries = evaluationQueryRepository.findAllById(queryIds);
      if (queries.isEmpty()) {
        log.warn("평가할 쿼리가 없습니다");
        return;
      }

      int totalBatches = 0;
      for (EvaluationQuery query : queries) {
        List<QueryProductMapping> mappings =
            queryProductMappingRepository.findByEvaluationQuery(query);
        if (mappings.isEmpty()) {
          continue;
        }

        // 배치로 나누어 큐에 추가
        Map<String, ProductDocument> productMap =
            evaluationWorker.getProductsBulk(
                mappings.stream().map(QueryProductMapping::getProductId).toList());

        List<ProductDocument> validProducts = new ArrayList<>();
        List<QueryProductMapping> validMappings = new ArrayList<>();

        for (QueryProductMapping mapping : mappings) {
          ProductDocument product = productMap.get(mapping.getProductId());
          if (product != null) {
            validProducts.add(product);
            validMappings.add(mapping);
          }
        }

        // 배치 크기로 나누어 큐에 추가
        for (int i = 0; i < validProducts.size(); i += batchSize) {
          int endIdx = Math.min(i + batchSize, validProducts.size());
          EvaluationBatch batch =
              new EvaluationBatch(
                  query.getQuery(),
                  validProducts.subList(i, endIdx),
                  validMappings.subList(i, endIdx),
                  query);
          evaluationQueue.offer(batch);
          totalBatches++;
          totalBatchesAdded.incrementAndGet();
        }
      }

      log.info("총 {}개 배치를 큐에 추가 완료", totalBatches);

      // 모든 작업이 완료될 때까지 대기
      waitForCompletion(totalBatches, progressCallback);

    } finally {
      // Health check 스레드 종료
      rateLimitManager.stopHealthCheck();
      log.info("LLM 평가 완료 - Health check 스레드 종료");
    }
  }

  private void waitForCompletion(int totalBatches, ProgressCallback progressCallback) {
    // 초기 대기: 워커들이 큐에서 작업을 가져갈 시간을 줌
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    int emptyCheckCount = 0;
    while (true) {
      int queueSize = evaluationQueue.size();
      int activeCount = activeWorkers.get();
      int addedCount = totalBatchesAdded.get();
      int processedCount = totalBatchesProcessed.get();

      log.info(
          "진행 상황: 큐 대기 {}개, 활성 Worker {}개, 처리 완료 {}/{}",
          queueSize,
          activeCount,
          processedCount,
          addedCount);

      // 진행률 콜백 호출
      if (progressCallback != null && addedCount > 0) {
        // 30%에서 시작해서 90%까지 진행 (60% 구간)
        int progress = Math.min(90, 30 + (int) ((processedCount * 60.0) / addedCount));
        String message = String.format("LLM 평가 진행 중: %d/%d 배치 완료", processedCount, addedCount);
        progressCallback.updateProgress(progress, message);
      }

      // 모든 배치가 처리되었는지 확인
      if (addedCount > 0 && processedCount >= addedCount) {
        log.info("모든 배치 처리 완료: {}/{}", processedCount, addedCount);
        break;
      }

      // 큐가 비어있고 활성 워커가 없을 때 (fallback)
      if (queueSize == 0 && activeCount == 0) {
        emptyCheckCount++;
        // 3번 연속으로 비어있으면 완료로 간주
        if (emptyCheckCount >= 3) {
          log.info("큐와 워커가 모두 비어있음 - 완료로 간주");
          break;
        }
      } else {
        emptyCheckCount = 0;
      }

      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    log.info("모든 평가 작업 완료 - 총 {}개 배치 처리", totalBatchesProcessed.get());
  }

  // 평가 배치 데이터 클래스
  private static class EvaluationBatch {
    final String query;
    final List<ProductDocument> products;
    final List<QueryProductMapping> mappings;
    final EvaluationQuery evaluationQuery;

    EvaluationBatch(
        String query,
        List<ProductDocument> products,
        List<QueryProductMapping> mappings,
        EvaluationQuery evaluationQuery) {
      this.query = query;
      this.products = products;
      this.mappings = mappings;
      this.evaluationQuery = evaluationQuery;
    }
  }
}
