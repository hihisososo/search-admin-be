package com.yjlee.search.evaluation.repository;

import com.yjlee.search.evaluation.model.EvaluationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationResultRepository extends JpaRepository<EvaluationResult, Long> {}
