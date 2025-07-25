package com.yjlee.search.evaluation.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TestPromptResponse {
  private String prompt;
  private String response;
  private long responseTimeMs;
} 