package com.yjlee.search.evaluation.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationQueryListResponse {

  private List<EvaluationQueryDto> queries;
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
  public static class EvaluationQueryDto {
    private Long id;
    private String query;
    private Integer documentCount; // 전체 문서수
    private Integer correctCount; // 정답개수 (isRelevant = true)
    private Integer incorrectCount; // 오답개수 (isRelevant = false)
    private Integer unspecifiedCount; // 미지정개수 (isRelevant = null)
    private LocalDateTime createdAt; // 생성일
    private LocalDateTime updatedAt; // 수정일
  }
}
