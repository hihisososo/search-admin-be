package com.yjlee.search.evaluation.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AsyncTaskStatus {
  PENDING("대기중", "PENDING"),
  IN_PROGRESS("진행중", "IN_PROGRESS"),
  COMPLETED("완료", "COMPLETED"),
  FAILED("실패", "FAILED");

  private final String displayName;
  private final String code;
}
