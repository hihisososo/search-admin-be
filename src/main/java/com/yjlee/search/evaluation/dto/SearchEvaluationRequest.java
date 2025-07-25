package com.yjlee.search.evaluation.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchEvaluationRequest {
  private String query;
  private int topK = 100;
}
