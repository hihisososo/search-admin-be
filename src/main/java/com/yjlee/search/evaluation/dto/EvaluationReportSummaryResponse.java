package com.yjlee.search.evaluation.dto;

import java.time.LocalDateTime;
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
public class EvaluationReportSummaryResponse {

  private Long id;
  private String reportName;
  private Integer totalQueries;
  private Double averageNdcg20;
  private Double averageRecall300;
  private LocalDateTime createdAt;
}
