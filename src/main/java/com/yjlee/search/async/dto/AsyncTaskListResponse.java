package com.yjlee.search.async.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "비동기 작업 목록 응답")
public class AsyncTaskListResponse {

  @Schema(description = "비동기 작업 목록")
  private List<AsyncTaskResponse> tasks;

  @Schema(description = "전체 작업 수", example = "100")
  private Long totalCount;

  @Schema(description = "전체 페이지 수", example = "10")
  private Integer totalPages;

  @Schema(description = "현재 페이지 번호", example = "0")
  private Integer currentPage;

  @Schema(description = "페이지 크기", example = "10")
  private Integer size;

  @Schema(description = "다음 페이지 존재 여부", example = "true")
  private Boolean hasNext;

  @Schema(description = "이전 페이지 존재 여부", example = "false")
  private Boolean hasPrevious;
}
