package com.yjlee.search.deployment.enums;

public enum DeploymentType {
  INDEXING("색인"),
  DEPLOYMENT("배포"),
  CLEANUP("정리");

  private final String description;

  DeploymentType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
