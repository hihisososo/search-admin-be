package com.yjlee.search.evaluation.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "evaluation_report_details",
    indexes = {@Index(name = "idx_report_detail_report_id", columnList = "report_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationReportDetail {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "report_id", nullable = false)
  private EvaluationReport report;

  @Column(nullable = false)
  private String query;

  @Column(name = "relevant_count")
  private Integer relevantCount;

  @Column(name = "retrieved_count")
  private Integer retrievedCount;

  @Column(name = "correct_count")
  private Integer correctCount;

  @Column(name = "precision_at20")
  private Double precisionAt20;

  @Column(name = "recall_at300")
  private Double recallAt300;
}
