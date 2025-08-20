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
  private Double ndcg20; // NDCG@20
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
    private Double ndcgAt20;
    private Double recallAt300;
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
