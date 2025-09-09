package com.yjlee.search.dictionary.typo.dto;

public record TypoSyncResponse(
    boolean success, String message, String environment, long timestamp) {
  public static TypoSyncResponse success(String environment) {
    return new TypoSyncResponse(true, "오타교정 사전 실시간 반영 완료", environment, System.currentTimeMillis());
  }
}
