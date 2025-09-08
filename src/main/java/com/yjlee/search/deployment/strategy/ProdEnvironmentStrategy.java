package com.yjlee.search.deployment.strategy;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.service.ElasticsearchSynonymService;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProdEnvironmentStrategy implements EnvironmentDeploymentStrategy {

  private final DictionaryDataDeploymentService dictionaryDeploymentService;
  private final ElasticsearchSynonymService elasticsearchSynonymService;

  @Override
  public boolean canDeploy(IndexEnvironment environment) {
    // 운영 환경은 항상 배포 가능
    return true;
  }

  @Override
  public void validateDeployment(IndexEnvironment sourceEnv, IndexEnvironment targetEnv) {
    // 운영 환경 배포는 추가 검증 필요 없음
    log.info("운영 환경 배포 검증 통과");
  }

  @Override
  public void prepareDeployment(IndexEnvironment environment) {
    log.info("운영 환경 배포 준비 - 사전 배포 시작");
    deployDictionariesToProd();
  }

  @Override
  public void postDeployment(IndexEnvironment environment) {
    log.info("운영 환경 배포 후처리 완료: {}", environment.getIndexName());
    // 필요시 추가 후처리 작업
  }

  @Override
  public String getEnvironmentName() {
    return "PROD";
  }

  private void deployDictionariesToProd() {
    try {
      // 모든 사전을 운영 환경으로 배포
      dictionaryDeploymentService.deployAllToProd();

      // 실시간 동기화 (캐시 업데이트 포함)
      dictionaryDeploymentService.realtimeSyncAll(DictionaryEnvironmentType.PROD);

      log.info("모든 사전 운영 환경 배포 및 실시간 동기화 완료");
    } catch (Exception e) {
      log.error("사전 운영 환경 배포 실패", e);
      throw new RuntimeException("사전 운영 환경 배포 실패", e);
    }
  }
}
