package com.yjlee.search.evaluation.repository;

import com.yjlee.search.evaluation.model.EvaluationQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationQueryRepository extends JpaRepository<EvaluationQuery, Long> {}
