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
  private Double averageNdcg;
  private Double ndcgAt10; // 평균 nDCG@10
  private Double ndcgAt20; // 평균 nDCG@20
  private Double mrrAt10; // 평균 MRR@10
  private Double recallAt50; // 평균 Recall@50
  private Double map; // MAP (Mean Average Precision)
  private Double recallAt300; // 평균 Recall@300
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
    private Double ndcg;
    private Double ndcgAt10;
    private Double ndcgAt20;
    private Double mrrAt10;
    private Double recallAt50;
    private Double map; // Average Precision per query
    private Double recallAt300;
    private Integer relevantCount;
    private Integer retrievedCount;
    private Integer correctCount;
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
