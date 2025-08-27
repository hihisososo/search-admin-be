package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.index.dto.ProductDocument;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMBatchProcessor {
  
  private final LLMQueryEvaluationWorker evaluationWorker;
  private final LLMRateLimitManager rateLimitManager;
  
  @Async("evaluationTaskExecutor")
  public void processBatchAsync(
      String query,
      List<ProductDocument> products,
      List<QueryProductMapping> mappings,
      EvaluationQuery evaluationQuery,
      Runnable onSuccess,
      Runnable onRateLimit) {
    try {
      log.info("배치 처리 시작: {} ({} 상품)", query, products.size());

      // evaluationWorker의 평가 로직 호출
      evaluationWorker.processSingleBatch(query, products, mappings, evaluationQuery);

      // 성공 콜백
      onSuccess.run();
      log.info("배치 처리 완료: {}", query);

    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("429")) {
        log.warn("Rate limit 감지 - 모든 작업 중단");
        rateLimitManager.setRateLimitActive();
        // Rate limit 콜백
        onRateLimit.run();
      } else {
        log.error("배치 처리 실패", e);
        // 실패해도 성공으로 처리 (카운트 증가)
        onSuccess.run();
      }
    }
  }
}