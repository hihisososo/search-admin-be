package com.yjlee.search.evaluation.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AsyncTaskType {
  QUERY_GENERATION("쿼리 자동생성", "QUERY_GENERATION"),
  CANDIDATE_GENERATION("후보군 생성", "CANDIDATE_GENERATION"),
  LLM_EVALUATION("후보군 자동평가", "LLM_EVALUATION");

  private final String displayName;
  private final String code;
}
