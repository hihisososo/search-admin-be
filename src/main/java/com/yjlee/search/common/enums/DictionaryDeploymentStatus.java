package com.yjlee.search.common.enums;

public enum DictionaryDeploymentStatus {
  PENDING("대기중"),
  DEPLOYING("배포중"),
  DEPLOYED("배포완료"),
  FAILED("배포실패");

  private final String description;

  DictionaryDeploymentStatus(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public boolean canDeploy() {
    return this == PENDING || this == FAILED;
  }

  public boolean canDelete() {
    return this != DEPLOYING;
  }
}
