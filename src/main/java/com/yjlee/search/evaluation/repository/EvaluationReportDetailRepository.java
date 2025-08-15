package com.yjlee.search.evaluation.repository;

import com.yjlee.search.evaluation.model.EvaluationReport;
import com.yjlee.search.evaluation.model.EvaluationReportDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationReportDetailRepository
    extends JpaRepository<EvaluationReportDetail, Long> {
  List<EvaluationReportDetail> findByReport(EvaluationReport report);

  void deleteByReport(EvaluationReport report);
}
