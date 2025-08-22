package com.yjlee.search.evaluation.repository;

import com.yjlee.search.evaluation.model.EvaluationReport;
import com.yjlee.search.evaluation.model.EvaluationReportDocument;
import com.yjlee.search.evaluation.model.ReportDocumentType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationReportDocumentRepository
    extends JpaRepository<EvaluationReportDocument, Long> {
  List<EvaluationReportDocument> findByReportAndDocType(
      EvaluationReport report, ReportDocumentType docType);

  void deleteByReport(EvaluationReport report);

  @Modifying
  @Query("DELETE FROM EvaluationReportDocument d WHERE d.report.id = :reportId")
  void deleteByReportIdBulk(@Param("reportId") Long reportId);
}
