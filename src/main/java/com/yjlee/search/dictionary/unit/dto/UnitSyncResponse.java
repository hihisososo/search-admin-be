package com.yjlee.search.dictionary.unit.dto;

public record UnitSyncResponse(boolean success, String message, String environment) {
  public static UnitSyncResponse error(String environment) {
    return new UnitSyncResponse(false, "단위 사전은 실시간 반영이 불가능합니다. 인덱스를 재생성해야 적용됩니다.", environment);
  }
}
