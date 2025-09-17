package com.yjlee.search.deployment.enums;

public enum IndexStatus {
  ACTIVE("활성"),
  INACTIVE("비활성");

  private final String description;

  IndexStatus(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
