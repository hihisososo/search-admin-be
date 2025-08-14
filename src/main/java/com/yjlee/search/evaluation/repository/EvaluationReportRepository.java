package com.yjlee.search.evaluation.repository;

import com.yjlee.search.evaluation.model.EvaluationReport;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationReportRepository extends JpaRepository<EvaluationReport, Long> {

  List<EvaluationReport> findByOrderByCreatedAtDesc();

  List<EvaluationReport> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

  @Query("SELECT r FROM EvaluationReport r WHERE r.reportName LIKE %:keyword%")
  List<EvaluationReport> findByReportNameContaining(String keyword);

  EvaluationReport findTopByOrderByCreatedAtDesc();

  // 제목 검색(대소문자 무시) + 최신순 정렬
  List<EvaluationReport> findByReportNameContainingIgnoreCaseOrderByCreatedAtDesc(String keyword);
}
