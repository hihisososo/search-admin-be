package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationQueryService {

  private final EvaluationQueryRepository evaluationQueryRepository;

  public List<EvaluationQuery> getAllQueries() {
    return evaluationQueryRepository.findAll();
  }

  public Page<EvaluationQuery> getQueriesWithPaging(Pageable pageable) {
    return evaluationQueryRepository.findAll(pageable);
  }

  public Optional<EvaluationQuery> getQueryById(Long queryId) {
    return evaluationQueryRepository.findById(queryId);
  }

  public Optional<EvaluationQuery> findByQuery(String query) {
    return evaluationQueryRepository.findByQuery(query);
  }

  @Transactional
  public EvaluationQuery createQuery(String query) {
    log.info("쿼리 생성: {}", query);

    Optional<EvaluationQuery> existingQuery = evaluationQueryRepository.findByQuery(query);
    if (existingQuery.isPresent()) {
      log.warn("이미 존재하는 쿼리입니다: {}", query);
      throw new IllegalArgumentException("이미 존재하는 쿼리입니다: " + query);
    }

    EvaluationQuery evaluationQuery = EvaluationQuery.builder().query(query).build();
    return evaluationQueryRepository.save(evaluationQuery);
  }

  @Transactional
  public EvaluationQuery updateQuery(Long queryId, String newQuery, Boolean reviewed) {
    log.info("쿼리 수정: ID={}, 새 쿼리={}, reviewed={}", queryId, newQuery, reviewed);

    Optional<EvaluationQuery> existing = evaluationQueryRepository.findById(queryId);
    if (existing.isEmpty()) {
      throw new IllegalArgumentException("쿼리를 찾을 수 없습니다: " + queryId);
    }

    EvaluationQuery query = existing.get();

    String valueToUse = query.getQuery();
    if (newQuery != null && !newQuery.trim().isEmpty() && !query.getQuery().equals(newQuery)) {
      Optional<EvaluationQuery> duplicateQuery = evaluationQueryRepository.findByQuery(newQuery);
      if (duplicateQuery.isPresent()) {
        log.warn("수정하려는 쿼리가 이미 존재합니다: {}", newQuery);
        throw new IllegalArgumentException("이미 존재하는 쿼리입니다: " + newQuery);
      }
      valueToUse = newQuery.trim();
    }

    EvaluationQuery updatedQuery =
        EvaluationQuery.builder().id(query.getId()).query(valueToUse).build();
    return evaluationQueryRepository.save(updatedQuery);
  }

  @Transactional
  public void deleteQueries(List<Long> queryIds) {
    log.info("쿼리 일괄 삭제: {}개 (관련 매핑들도 자동 삭제됨)", queryIds.size());
    evaluationQueryRepository.deleteAllById(queryIds);
  }

  @Transactional
  public EvaluationQuery createQuerySafely(String query) {
    try {
      return createQuery(query);
    } catch (Exception e) {
      if (e.getMessage().contains("이미 존재하는 쿼리")) {
        log.debug("기존 쿼리 반환: {}", query);
        return evaluationQueryRepository.findByQuery(query).orElse(null);
      }
      throw e;
    }
  }
}
