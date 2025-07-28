package com.yjlee.search.evaluation.repository;

import com.yjlee.search.evaluation.model.EvaluationQuery;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationQueryRepository extends JpaRepository<EvaluationQuery, Long> {
  Optional<EvaluationQuery> findByQuery(String query);

  @Query("SELECT e.query FROM EvaluationQuery e")
  List<String> findAllQueryStrings();
}
