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
    private Long candidateId;
    private String productId;
    private String productName;
    private String specs;
    private Integer score;
    private String evaluationReason;
    private Double confidence;
  }
}
