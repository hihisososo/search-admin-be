package com.yjlee.search.evaluation.dto;

import com.yjlee.search.search.dto.SearchMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "비동기 평가 실행 요청")
public class EvaluationExecuteAsyncRequest {

  @NotBlank(message = "리포트명은 필수입니다")
  @Schema(description = "평가 리포트명", example = "2025-01 검색 품질 평가", required = true)
  private String reportName;

  @Schema(description = "검색 모드 (KEYWORD_ONLY: BM25, VECTOR_ONLY: 벡터, HYBRID_RRF: RRF 융합)", example = "KEYWORD_ONLY", defaultValue = "KEYWORD_ONLY")
  @Builder.Default
  private SearchMode searchMode = SearchMode.KEYWORD_ONLY;

  @Schema(description = "RRF 알고리즘 K 상수 (rank + k)", example = "60", defaultValue = "60")
  @Min(1)
  @Builder.Default
  private Integer rrfK = 60;

  @Schema(description = "하이브리드 검색 시 각 검색의 상위 K개 결과", example = "100", defaultValue = "100")
  @Min(1)
  @Builder.Default
  private Integer hybridTopK = 100;
}
