package com.yjlee.search.search.service.builder.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueryContext {
  private final String originalQuery;
  private final String processedQuery;
  private final Boolean applyTypoCorrection;

  // 추가 필드 - 분석 결과
  private final String queryWithoutTerms;
  private final boolean isQueryEmptyAfterRemoval;

  public boolean isEmpty() {
    return processedQuery == null || processedQuery.trim().isEmpty();
  }

  public boolean hasQueryWithoutTerms() {
    return queryWithoutTerms != null && !queryWithoutTerms.trim().isEmpty();
  }
}
