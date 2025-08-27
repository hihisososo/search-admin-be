package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMCandidateEvaluationService {

  private final EvaluationQueryRepository evaluationQueryRepository;
  private final LLMQueuedEvaluationService queuedEvaluationService;

  public void evaluateAllCandidates() {
    evaluateAllCandidates(null);
  }

  public void evaluateAllCandidates(ProgressCallback progressCallback) {
    log.info("전체 모든 쿼리의 후보군 LLM 평가 시작");

    List<EvaluationQuery> queries = evaluationQueryRepository.findAll();
    if (queries.isEmpty()) {
      log.warn("⚠️ 평가할 쿼리가 없습니다. 먼저 쿼리를 생성해주세요.");
      return;
    }

    log.info("평가 대상 쿼리: {}개", queries.size());
    evaluateCandidatesForQueries(
        queries.stream().map(EvaluationQuery::getId).toList(), progressCallback);

    log.info("전체 모든 쿼리의 후보군 LLM 평가 완료");
  }

  public void evaluateCandidatesForQueries(List<Long> queryIds) {
    evaluateCandidatesForQueries(queryIds, null);
  }

  public void evaluateCandidatesForQueries(List<Long> queryIds, ProgressCallback progressCallback) {
    log.info("선택된 쿼리들의 후보군 LLM 평가 시작 (큐 기반): {}개 쿼리", queryIds.size());

    // 큐 기반 평가 서비스 호출
    queuedEvaluationService.evaluateCandidatesForQueries(queryIds, progressCallback);

    log.info("선택된 쿼리들의 후보군 LLM 평가 완료 (큐 기반)");
  }
}
