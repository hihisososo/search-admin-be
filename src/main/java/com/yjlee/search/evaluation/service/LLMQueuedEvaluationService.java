package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
import com.yjlee.search.index.dto.ProductDocument;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMQueuedEvaluationService {

  private final LLMRateLimitManager rateLimitManager;
  private final LLMQueryEvaluationWorker evaluationWorker;
  private final EvaluationQueryRepository evaluationQueryRepository;
  private final QueryProductMappingRepository queryProductMappingRepository;

  @Value("${evaluation.llm.batch-size:10}")
  private int batchSize;

  @Value("${evaluation.llm.worker-threads:5}")
  private int workerThreads;

  // 평가 작업 큐
  private final BlockingQueue<EvaluationBatch> evaluationQueue = new LinkedBlockingQueue<>();
  private final List<Thread> workers = new ArrayList<>();
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final AtomicInteger activeWorkers = new AtomicInteger(0);

  @PostConstruct
  public void init() {
    log.info("LLM 큐 평가 서비스 시작 - Worker 스레드: {}개", workerThreads);
    for (int i = 0; i < workerThreads; i++) {
      final int workerId = i;
      Thread worker =
          new Thread(
              () -> {
                workerLoop(workerId);
              },
              "llm-queue-worker-" + i);
      worker.start();
      workers.add(worker);
    }
  }

  @PreDestroy
  public void shutdown() {
    log.info("LLM 큐 평가 서비스 종료 중...");
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
    log.info("LLM 큐 평가 서비스 종료 완료");
  }

  private void workerLoop(int workerId) {
    log.info("Worker {} 시작", workerId);
    while (running.get()) {
      try {
        // Rate limit 상태일 때만 대기
        if (rateLimitManager.isRateLimited()) {
          rateLimitManager.waitIfRateLimited();
          continue; // Rate limit 해제 후 다시 시도
        }

        // 큐에서 작업 가져오기 (최대 1초 대기)
        EvaluationBatch batch = evaluationQueue.poll(1, TimeUnit.SECONDS);
        if (batch == null) {
          continue;
        }

        activeWorkers.incrementAndGet();
        log.info("Worker {} - 배치 처리 시작: {} ({} 상품)", workerId, batch.query, batch.products.size());

        // 평가 수행
        processBatch(batch);

        log.info("Worker {} - 배치 처리 완료", workerId);

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.error("Worker {} - 오류 발생", workerId, e);
      } finally {
        activeWorkers.decrementAndGet();
      }
    }
    log.info("Worker {} 종료", workerId);
  }

  private void processBatch(EvaluationBatch batch) {
    try {
      // evaluationWorker의 평가 로직 호출
      evaluationWorker.processSingleBatch(
          batch.query, batch.products, batch.mappings, batch.evaluationQuery);
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("429")) {
        log.warn("Rate limit 감지 - 모든 작업 중단");
        rateLimitManager.setRateLimitActive();
        // 실패한 배치를 다시 큐에 넣기
        try {
          evaluationQueue.put(batch);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      } else {
        log.error("배치 처리 실패", e);
      }
    }
  }

  public void evaluateCandidatesForQueries(List<Long> queryIds) {
    log.info("큐 기반 LLM 평가 시작: {}개 쿼리", queryIds.size());

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
          try {
            evaluationQueue.put(batch);
            totalBatches++;
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("큐에 배치 추가 실패", e);
            return;
          }
        }
      }

      log.info("총 {}개 배치를 큐에 추가 완료", totalBatches);

      // 모든 작업이 완료될 때까지 대기
      waitForCompletion();

    } finally {
      // Health check 스레드 종료
      rateLimitManager.stopHealthCheck();
      log.info("LLM 평가 완료 - Health check 스레드 종료");
    }
  }

  private void waitForCompletion() {
    while (!evaluationQueue.isEmpty() || activeWorkers.get() > 0) {
      try {
        log.info("진행 상황: 큐 대기 {}개, 활성 Worker {}개", evaluationQueue.size(), activeWorkers.get());
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    log.info("모든 평가 작업 완료");
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
