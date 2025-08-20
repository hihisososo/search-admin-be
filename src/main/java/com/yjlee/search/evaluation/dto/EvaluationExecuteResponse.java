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
  private Double recall; // Recall@300
  private Double precision; // Precision@300
  private Double ndcg; // NDCG@20
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
    private Integer relevantCount;
    private Integer retrievedCount;
    private Integer correctCount;
    private List<DocumentInfo> missingDocuments;
    private List<DocumentInfo> wrongDocuments;
    private List<DocumentInfo> relevantDocuments;
    private List<DocumentInfo> retrievedDocuments;
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
