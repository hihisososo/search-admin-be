package com.yjlee.search.evaluation.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "evaluation_report_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationReportDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "report_id", nullable = false)
  private EvaluationReport report;

  @Column(nullable = false)
  private String query;

  @Column(name = "product_id", nullable = false, length = 64)
  private String productId;

  @Enumerated(EnumType.STRING)
  @Column(name = "doc_type", nullable = false, length = 16)
  private ReportDocumentType docType; // MISSING, WRONG

  @Column(name = "product_name", columnDefinition = "TEXT")
  private String productName;

  @Column(name = "product_specs", columnDefinition = "TEXT")
  private String productSpecs;

  @Column(name = "position")
  private Integer position;
}
