package com.yjlee.search.evaluation.dto;

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
public class AsyncTaskListResponse {

  private List<AsyncTaskResponse> tasks;
  private Long totalCount;
  private Integer totalPages;
  private Integer currentPage;
  private Integer size;
  private Boolean hasNext;
  private Boolean hasPrevious;
}
