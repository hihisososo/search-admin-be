package com.yjlee.search.evaluation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidatePreviewRequest {
  private String query;
  private Boolean useVector;
  private Boolean useMorph;
  private Boolean useBigram;
  private Integer perMethodLimit;
  private String vectorField;
  private Double vectorMinScore;
}


