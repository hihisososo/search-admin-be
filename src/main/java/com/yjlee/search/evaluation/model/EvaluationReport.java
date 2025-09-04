package com.yjlee.search.evaluation.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "evaluation_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class EvaluationReport {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String reportName;

  @Column(nullable = false)
  private Integer totalQueries;

  @Column(nullable = false, name = "average_precision20")
  private Double averagePrecision20;

  @Column(nullable = false, name = "average_recall300")
  private Double averageRecall300;

  @Column(name = "average_f1_score_at20")
  private Double averageF1ScoreAt20;

  @Column(columnDefinition = "TEXT")
  private String detailedResults;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
