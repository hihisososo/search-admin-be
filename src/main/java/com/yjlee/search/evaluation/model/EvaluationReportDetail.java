package com.yjlee.search.evaluation.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "evaluation_report_details")
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
}
