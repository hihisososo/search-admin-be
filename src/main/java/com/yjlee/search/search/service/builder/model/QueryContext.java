package com.yjlee.search.search.service.builder.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
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

  // 추가 필드 - 분석 결과
  private final String queryWithoutTerms; // 단위/모델 제거된 쿼리
  private final Map<String, Set<String>> expandedUnits; // 단위별 확장 결과 (원본 단위 -> 확장된 단위들)
  private final boolean isQueryEmptyAfterRemoval; // 제거 후 비어있는지

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

  public boolean hasQueryWithoutTerms() {
    return queryWithoutTerms != null && !queryWithoutTerms.trim().isEmpty();
  }

  public boolean hasExpandedUnits() {
    return expandedUnits != null && !expandedUnits.isEmpty();
  }
}
