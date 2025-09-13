package com.yjlee.search.async.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AsyncTaskType {
  CANDIDATE_GENERATION("후보군 생성"),
  LLM_EVALUATION("후보군 자동평가"),
  EVALUATION_EXECUTION("평가 실행"),
  INDEXING("상품 색인");

  private final String displayName;
}
