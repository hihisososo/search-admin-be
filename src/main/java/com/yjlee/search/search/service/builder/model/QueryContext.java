package com.yjlee.search.search.service.builder.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueryContext {
  private final String originalQuery;
  private final String processedQuery;
  private final List<String> units;
  private final List<String> models;
  private final Boolean applyTypoCorrection;

  public boolean hasUnits() {
    return units != null && !units.isEmpty();
  }

  public boolean hasModels() {
    return models != null && !models.isEmpty();
  }

  public boolean isEmpty() {
    return processedQuery == null || processedQuery.trim().isEmpty();
  }

  public boolean hasOnlyUnits() {
    return isEmpty() && hasUnits() && !hasModels();
  }

  public boolean hasOnlyModels() {
    return isEmpty() && !hasUnits() && hasModels();
  }

  public boolean hasUnitsAndModels() {
    return hasUnits() && hasModels();
  }
}
