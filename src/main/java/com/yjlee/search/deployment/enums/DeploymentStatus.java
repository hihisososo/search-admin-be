package com.yjlee.search.deployment.enums;

public enum DeploymentStatus {
  SUCCESS("성공"),
  FAILED("실패"),
  IN_PROGRESS("진행중"),
  COMPLETED("완료"),
  PARTIAL("부분완료");

  private final String description;

  DeploymentStatus(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
