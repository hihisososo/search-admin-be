package com.yjlee.search.common.enums;

public enum DictionaryEnvironmentType {
  CURRENT("현재"),
  DEV("개발"),
  PROD("운영");

  private final String description;

  DictionaryEnvironmentType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
