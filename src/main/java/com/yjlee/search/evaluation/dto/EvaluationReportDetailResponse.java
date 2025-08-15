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
  private Double averageNdcg;
  private Integer totalRelevantDocuments;
  private Integer totalRetrievedDocuments;
  private Integer totalCorrectDocuments;
  private LocalDateTime createdAt;
  private List<QueryDetail> queryDetails;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class QueryDetail {
    private String query;
    private Double ndcg;
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
