package com.yjlee.search.common.enums;

public enum EnvironmentType {
  CURRENT("현재"),
  DEV("개발"),
  PROD("운영");

  private final String description;

  EnvironmentType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
