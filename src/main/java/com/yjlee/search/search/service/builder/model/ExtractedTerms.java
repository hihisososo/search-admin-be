package com.yjlee.search.search.service.builder.model;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExtractedTerms {
  private final List<String> models;

  public static ExtractedTerms empty() {
    return ExtractedTerms.builder()
        .models(Collections.emptyList())
        .build();
  }

  public boolean hasModels() {
    return models != null && !models.isEmpty();
  }

  public boolean isEmpty() {
    return !hasModels();
  }
}
