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
  private Double recall300; // Recall@300
  private Double precision20; // Precision@20
  private Double f1ScoreAt20; // F1-Score@20
  private Integer totalQueries;
  private List<QueryEvaluationDetail> queryDetails;
  private LocalDateTime createdAt;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class QueryEvaluationDetail {
    private String query;
    private Integer relevantCount;
    private Integer retrievedCount;
    private Integer correctCount;
    private Double precisionAt20;
    private Double recallAt300;
    private Double f1ScoreAt20;
    private List<DocumentInfo> missingDocuments;
    private List<DocumentInfo> wrongDocuments;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class DocumentInfo {
    private String productId;
    private String productName;
    private String productSpecs;
  }
}
