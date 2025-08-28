package com.yjlee.search.evaluation.service;

import com.yjlee.search.evaluation.dto.EvaluationExecuteResponse;
import com.yjlee.search.evaluation.model.EvaluationReport;
import com.yjlee.search.evaluation.model.EvaluationReportDetail;
import com.yjlee.search.evaluation.model.EvaluationReportDocument;
import com.yjlee.search.evaluation.model.ReportDocumentType;
import com.yjlee.search.evaluation.repository.EvaluationReportDetailRepository;
import com.yjlee.search.evaluation.repository.EvaluationReportDocumentRepository;
import com.yjlee.search.evaluation.repository.EvaluationReportRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationReportPersistenceService {

  private final EvaluationReportRepository evaluationReportRepository;
  private final EvaluationReportDetailRepository reportDetailRepository;
  private final EvaluationReportDocumentRepository reportDocumentRepository;

  @Transactional
  public EvaluationReport saveEvaluationResults(
      String reportName,
      int totalQueries,
      double avgRecall300,
      double avgPrecision20,
      List<EvaluationExecuteResponse.QueryEvaluationDetail> queryDetails) {

    // 리포트 저장
    EvaluationReport report =
        saveEvaluationReport(reportName, totalQueries, avgRecall300, avgPrecision20);

    // 세부 결과를 구조화 테이블에 저장
    List<EvaluationReportDetail> detailRows = new ArrayList<>();
    List<EvaluationReportDocument> docRows = new ArrayList<>();

    for (EvaluationExecuteResponse.QueryEvaluationDetail d : queryDetails) {
      detailRows.add(
          EvaluationReportDetail.builder()
              .report(report)
              .query(d.getQuery())
              .relevantCount(d.getRelevantCount())
              .retrievedCount(d.getRetrievedCount())
              .correctCount(d.getCorrectCount())
              .precisionAt20(d.getPrecisionAt20())
              .recallAt300(d.getRecallAt300())
              .build());

      // MISSING, WRONG 타입만 저장
      if (d.getMissingDocuments() != null) {
        for (EvaluationExecuteResponse.DocumentInfo m : d.getMissingDocuments()) {
          docRows.add(
              EvaluationReportDocument.builder()
                  .report(report)
                  .query(d.getQuery())
                  .productId(m.getProductId())
                  .docType(ReportDocumentType.MISSING)
                  .productName(m.getProductName())
                  .productSpecs(m.getProductSpecs())
                  .build());
        }
      }
      if (d.getWrongDocuments() != null) {
        for (EvaluationExecuteResponse.DocumentInfo w : d.getWrongDocuments()) {
          docRows.add(
              EvaluationReportDocument.builder()
                  .report(report)
                  .query(d.getQuery())
                  .productId(w.getProductId())
                  .docType(ReportDocumentType.WRONG)
                  .productName(w.getProductName())
                  .productSpecs(w.getProductSpecs())
                  .build());
        }
      }
    }

    if (!detailRows.isEmpty()) reportDetailRepository.saveAll(detailRows);
    if (!docRows.isEmpty()) reportDocumentRepository.saveAll(docRows);

    log.info(
        "✅ 평가 결과 저장 완료: 리포트 ID {}, 상세 {}개, 문서 {}개",
        report.getId(),
        detailRows.size(),
        docRows.size());

    return report;
  }

  private EvaluationReport saveEvaluationReport(
      String reportName, int totalQueries, double averageRecall300, double averagePrecision20) {
    try {
      // JSON은 더 이상 저장하지 않음 (대용량 방지)
      EvaluationReport report =
          EvaluationReport.builder()
              .reportName(reportName)
              .totalQueries(totalQueries)
              .averageRecall300(averageRecall300)
              .averagePrecision20(averagePrecision20)
              .detailedResults(null)
              .build();

      EvaluationReport savedReport = evaluationReportRepository.save(report);
      log.info("✅ 리포트 저장 완료: ID {}", savedReport.getId());
      return savedReport;

    } catch (Exception e) {
      log.error("❌ 리포트 저장 실패", e);
      throw new RuntimeException("리포트 저장 중 오류가 발생했습니다", e);
    }
  }
}
