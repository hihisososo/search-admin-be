package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.dto.EvaluationQueryResponse;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.repository.EvaluationQueryRepository;
import com.yjlee.search.evaluation.repository.QueryProductMappingRepository;
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
  private final QueryProductMappingRepository queryProductMappingRepository;

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
  public EvaluationQueryResponse createQuery(String query) {
    log.info("쿼리 생성: {}", query);

    Optional<EvaluationQuery> existingQuery = evaluationQueryRepository.findByQuery(query);
    if (existingQuery.isPresent()) {
      log.warn("이미 존재하는 쿼리입니다: {}", query);
      throw new IllegalArgumentException("이미 존재하는 쿼리입니다: " + query);
    }

    EvaluationQuery evaluationQuery = EvaluationQuery.builder().query(query).build();
    EvaluationQuery saved = evaluationQueryRepository.save(evaluationQuery);
    return toDto(saved);
  }

  @Transactional
  public EvaluationQueryResponse updateQuery(Long queryId, String newQuery, Boolean reviewed) {
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
        EvaluationQuery.builder()
            .id(query.getId())
            .query(valueToUse)
            .createdAt(query.getCreatedAt())
            .updatedAt(query.getUpdatedAt())
            .build();
    EvaluationQuery saved = evaluationQueryRepository.save(updatedQuery);
    return toDto(saved);
  }

  @Transactional
  public void deleteQueries(List<Long> queryIds) {
    if (queryIds == null || queryIds.isEmpty()) {
      return;
    }

    log.info("쿼리 일괄 삭제 시작: {}개", queryIds.size());

    // 1. 먼저 관련 매핑들을 bulk delete로 삭제
    queryProductMappingRepository.deleteByQueryIds(queryIds);
    log.info("관련 매핑 삭제 완료");

    // 2. 그 다음 쿼리들을 삭제
    evaluationQueryRepository.deleteAllById(queryIds);
    log.info("쿼리 삭제 완료: {}개", queryIds.size());
  }

  @Transactional
  public EvaluationQuery createQuerySafely(String query) {
    try {
      EvaluationQueryResponse response = createQuery(query);
      return evaluationQueryRepository.findById(response.getId()).orElse(null);
    } catch (Exception e) {
      if (e.getMessage().contains("이미 존재하는 쿼리")) {
        log.debug("기존 쿼리 반환: {}", query);
        return evaluationQueryRepository.findByQuery(query).orElse(null);
      }
      throw e;
    }
  }

  private EvaluationQueryResponse toDto(EvaluationQuery entity) {
    return EvaluationQueryResponse.builder()
        .id(entity.getId())
        .query(entity.getQuery())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
