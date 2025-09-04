package com.yjlee.search.evaluation.dto;

import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryDocumentMappingResponse {

  private String query;
  private List<ProductDocumentDto> documents;
  private Long totalCount;
  private Integer totalPages;
  private Integer currentPage;
  private Integer size;
  private Boolean hasNext;
  private Boolean hasPrevious;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ProductDocumentDto {
    private Long id;
    private String productId;
    private String productName;
    private String productSpecs;
    private String productCategory;
    private Integer relevanceScore;
    private String evaluationReason;
    private Double confidence;
    private String searchSource; // BM25, BIGRAM, VECTOR, MULTIPLE
  }
}
