package com.yjlee.search.deployment.strategy;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DevEnvironmentStrategy implements EnvironmentDeploymentStrategy {

  private final DictionaryDataDeploymentService dictionaryDeploymentService;

  @Override
  public boolean canDeploy(IndexEnvironment environment) {
    // 개발 환경은 색인 중이 아니면 언제든 배포 가능
    return !environment.getIsIndexing();
  }

  @Override
  public void validateDeployment(IndexEnvironment sourceEnv, IndexEnvironment targetEnv) {
    if (sourceEnv.getIndexStatus() != IndexEnvironment.IndexStatus.ACTIVE) {
      throw new IllegalStateException("개발 환경에 활성화된 색인이 없습니다.");
    }

    if (sourceEnv.getIsIndexing()) {
      throw new IllegalStateException("개발 환경에서 색인이 진행 중입니다.");
    }
  }

  @Override
  public void prepareDeployment(IndexEnvironment environment) {
    log.info("개발 환경 배포 준비: {}", environment.getIndexName());
    // 개발 환경은 특별한 준비 작업 없음
  }

  @Override
  public void postDeployment(IndexEnvironment environment) {
    log.info("개발 환경 배포 후처리 시작: {}", environment.getIndexName());

    // 개발 환경 사전 데이터 초기화
    try {
      log.info("개발 환경 사전 데이터 초기화 시작");
      dictionaryDeploymentService.deleteAllByEnvironment(DictionaryEnvironmentType.DEV);
      log.info("개발 환경 사전 데이터 초기화 완료");
    } catch (Exception e) {
      log.error("개발 환경 사전 데이터 초기화 실패", e);
      // 데이터 초기화 실패는 배포를 롤백하지 않음 (경고만)
    }

    log.info("개발 환경 배포 후처리 완료: {}", environment.getIndexName());
  }

  @Override
  public String getEnvironmentName() {
    return "DEV";
  }
}
