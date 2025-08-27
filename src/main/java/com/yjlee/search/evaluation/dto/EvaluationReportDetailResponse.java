package com.yjlee.search.evaluation.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationReportDetailResponse {

  private Long id;
  private String reportName;
  private Integer totalQueries;
  private Double averageRecall300; // Recall@300 평균
  private Double averagePrecision20; // Precision@20 평균
  private LocalDateTime createdAt;
  private List<QueryDetail> queryDetails;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class QueryDetail {
    private String query;
    private Integer relevantCount;
    private Integer retrievedCount;
    private Integer correctCount;
    private Double precisionAt20;
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
