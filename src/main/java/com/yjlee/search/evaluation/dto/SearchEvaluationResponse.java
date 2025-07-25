package com.yjlee.search.evaluation.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SearchEvaluationResponse {
  private String query;
  private int topK;
  private int totalSearchResults;
  private int totalGroundTruth;
  private int correctResults;
  private double precision;
  private double recall;
  private List<String> searchResultIds;
  private List<String> groundTruthIds;
  private List<String> correctIds;
}
