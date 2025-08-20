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
  private Double recall; // Recall@300
  private Double precision; // Precision@300
  private Double ndcg; // NDCG@20
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
    private Integer relevantCount;
    private Integer retrievedCount;
    private Integer correctCount;
    private java.util.List<RetrievedDocument> retrievedDocuments; // 실제 검색 결과(순서 유지)
    private java.util.List<GroundTruthDocument> groundTruthDocuments; // 정답셋(점수 내림차순)
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

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RetrievedDocument {
    private Integer rank; // 1-base
    private String productId;
    private String productName;
    private String productSpecs;
    private Integer gain; // 0/1/2
    private Boolean isRelevant; // gain > 0
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class GroundTruthDocument {
    private String productId;
    private String productName;
    private String productSpecs;
    private Integer score; // relevanceScore 저장값(2/1/0/-1/-100)
  }
}
