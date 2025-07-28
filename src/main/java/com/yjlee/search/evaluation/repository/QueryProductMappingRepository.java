package com.yjlee.search.evaluation.repository;

import com.yjlee.search.evaluation.dto.QueryStatsDto;
import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.model.RelevanceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryProductMappingRepository extends JpaRepository<QueryProductMapping, Long> {
  List<QueryProductMapping> findByEvaluationQuery(EvaluationQuery evaluationQuery);

  List<QueryProductMapping> findByEvaluationQueryAndRelevanceStatus(
      EvaluationQuery evaluationQuery, RelevanceStatus relevanceStatus);

  Optional<QueryProductMapping> findByEvaluationQueryAndProductId(
      EvaluationQuery evaluationQuery, String productId);

  @Query(
      """
      SELECT new com.yjlee.search.evaluation.dto.QueryStatsDto(
          eq.query,
          COUNT(m),
          SUM(CASE WHEN m.relevanceStatus = 'RELEVANT' THEN 1 ELSE 0 END),
          SUM(CASE WHEN m.relevanceStatus = 'IRRELEVANT' THEN 1 ELSE 0 END),
          SUM(CASE WHEN m.relevanceStatus = 'UNSPECIFIED' THEN 1 ELSE 0 END)
      )
      FROM QueryProductMapping m
      JOIN m.evaluationQuery eq
      GROUP BY eq.query
      """)
  List<QueryStatsDto> findQueryStats();
}
