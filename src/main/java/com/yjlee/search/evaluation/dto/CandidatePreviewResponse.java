package com.yjlee.search.evaluation.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CandidatePreviewResponse {
  private String query;
  private List<String> vectorIds;
  private List<String> morphIds;
  private List<String> bigramIds;
}
