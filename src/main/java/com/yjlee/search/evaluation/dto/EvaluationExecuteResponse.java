package com.yjlee.search.evaluation.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationExecuteResponse {

  private Long reportId;
  private String reportName;
  private Double averagePrecision;
  private Double averageRecall;
  private Double averageF1Score;
  private Integer totalQueries;
  private Integer totalRelevantDocuments;
  private Integer totalRetrievedDocuments;
  private Integer totalCorrectDocuments;
  private List<QueryEvaluationDetail> queryDetails;
  private LocalDateTime createdAt;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class QueryEvaluationDetail {
    private String query;
    private Double precision;
    private Double recall;
    private Double f1Score;
    private Integer relevantCount;
    private Integer retrievedCount;
    private Integer correctCount;
    private List<String> missingDocuments;
    private List<String> wrongDocuments;
  }
}
