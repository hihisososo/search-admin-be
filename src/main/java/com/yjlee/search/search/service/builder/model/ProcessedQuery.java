package com.yjlee.search.search.service.builder.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProcessedQuery {
  private final String original;
  private final String normalized;
  private final String withoutUnits;
  private final String withoutModels;
  private final String corrected;

  public static ProcessedQuery of(String query) {
    return ProcessedQuery.builder()
        .original(query)
        .normalized(query)
        .withoutUnits(query)
        .withoutModels(query)
        .corrected(query)
        .build();
  }

  public boolean isEmpty() {
    return corrected == null || corrected.trim().isEmpty();
  }

  public String getFinalQuery() {
    return corrected != null ? corrected : normalized;
  }
}
