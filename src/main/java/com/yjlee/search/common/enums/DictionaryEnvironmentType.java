package com.yjlee.search.common.enums;

public enum DictionaryEnvironmentType {
  CURRENT("현재"), // 현재 편집 중인 사전
  DEV("개발"), // 개발 환경 사전
  PROD("운영"); // 운영 환경 사전

  private final String description;

  DictionaryEnvironmentType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
