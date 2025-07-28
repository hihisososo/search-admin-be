package com.yjlee.search.evaluation.dto;

import com.yjlee.search.evaluation.model.RelevanceStatus;
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
    private String productId;
    private String productName;
    private String specs;
    private RelevanceStatus relevanceStatus;
    private String evaluationReason;
  }
}
