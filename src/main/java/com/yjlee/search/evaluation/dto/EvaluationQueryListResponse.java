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
    private Integer score2Count; // 2점 개수
    private Integer score1Count; // 1점 개수
    private Integer score0Count; // 0점 개수
    private Integer scoreMinus1Count; // 사람 확인 필요(-1) 개수
    private Integer unevaluatedCount; // 미평가(null) 개수
    private LocalDateTime createdAt; // 생성일
    private LocalDateTime updatedAt; // 수정일
  }
}
