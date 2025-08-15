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

  @Column(name = "ndcg")
  private Double ndcg;

  @Column(name = "ndcg_at_10")
  private Double ndcgAt10;

  @Column(name = "ndcg_at_20")
  private Double ndcgAt20;

  @Column(name = "mrr_at_10")
  private Double mrrAt10;

  @Column(name = "recall_at_50")
  private Double recallAt50;

  @Column(name = "ap")
  private Double averagePrecision;

  @Column(name = "recall_at_300")
  private Double recallAt300;

  @Column(name = "relevant_count")
  private Integer relevantCount;

  @Column(name = "retrieved_count")
  private Integer retrievedCount;

  @Column(name = "correct_count")
  private Integer correctCount;
}
