package com.yjlee.search.dictionary.deployment.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeploymentStatus {
  PENDING("배포 대기 중"),
  DEPLOYING("배포 진행 중"),
  DEPLOYED("배포 완료"),
  FAILED("배포 실패");

  private final String description;

  public boolean canDeploy() {
    return this == PENDING || this == FAILED;
  }

  public boolean canDelete() {
    return this != DEPLOYING;
  }

  public boolean isInProgress() {
    return this == DEPLOYING;
  }

  public boolean isCompleted() {
    return this == DEPLOYED || this == FAILED;
  }
}
