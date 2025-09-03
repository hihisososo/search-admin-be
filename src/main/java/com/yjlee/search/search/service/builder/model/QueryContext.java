package com.yjlee.search.search.service.builder.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueryContext {
  private final String originalQuery;
  private final String processedQuery;
  private final List<String> models;
  private final Boolean applyTypoCorrection;

  // 추가 필드 - 분석 결과
  private final String queryWithoutTerms; // 모델 제거된 쿼리
  private final boolean isQueryEmptyAfterRemoval; // 제거 후 비어있는지

  public boolean hasModels() {
    return models != null && !models.isEmpty();
  }

  public boolean isEmpty() {
    return processedQuery == null || processedQuery.trim().isEmpty();
  }

  public boolean hasOnlyModels() {
    return isEmpty() && hasModels();
  }

  public boolean hasQueryWithoutTerms() {
    return queryWithoutTerms != null && !queryWithoutTerms.trim().isEmpty();
  }
}
