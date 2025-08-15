package com.yjlee.search.evaluation.repository;

import com.yjlee.search.evaluation.model.EvaluationQuery;
import com.yjlee.search.evaluation.model.QueryProductMapping;
import com.yjlee.search.evaluation.model.RelevanceStatus;
import com.yjlee.search.evaluation.repository.projection.QueryStatsProjection;
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
      SELECT
          eq.query as query,
          COUNT(m) as documentCount,
          SUM(CASE WHEN m.relevanceScore = 2 THEN 1 ELSE 0 END) as score2Count,
          SUM(CASE WHEN m.relevanceScore = 1 THEN 1 ELSE 0 END) as score1Count,
          SUM(CASE WHEN m.relevanceScore = 0 THEN 1 ELSE 0 END) as score0Count,
          SUM(CASE WHEN m.relevanceScore = -1 THEN 1 ELSE 0 END) as scoreMinus1Count
      FROM QueryProductMapping m
      JOIN m.evaluationQuery eq
      GROUP BY eq.query
      """)
  List<QueryStatsProjection> findQueryStats();
}
