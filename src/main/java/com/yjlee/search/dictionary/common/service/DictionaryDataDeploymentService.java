package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.enums.EnvironmentType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryDataDeploymentService {

  private final List<DictionaryService> dictionaryServices;

  @Transactional
  public void preIndexingAll() {
    log.info("색인 전 사전 준비작업 시작");

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.info("{} 사전 색인 전 준비 중", serviceName);
        dictionaryService.preIndexing();
        log.info("{} preIndexing 완료", serviceName);
      } catch (Exception e) {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.error("{} 사전 색인 전 준비 실패", serviceName, e);
        throw new RuntimeException(serviceName + " 사전 색인 준비 실패", e);
      }
    }

    log.info("색인 전 사전 준비작업 완료");
  }

  @Transactional
  public void postIndexingAll() {
    log.info("색인 후 사전 정리작업 시작");

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.debug("{} 사전 색인 후 정리 중", serviceName);
        dictionaryService.postIndexing();
        log.info("{} postIndexing 완료", serviceName);
      } catch (Exception e) {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.error("{} 사전 색인 후 정리 실패", serviceName, e);
        throw new RuntimeException(serviceName + " 사전 색인 후 정리 실패", e);
      }
    }

    log.info("색인 후 사전 정리작업 완료");
  }

  @Transactional
  public void preDeployAll() {
    log.info("배포 전 사전 준비작업 시작");

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.info("{} 사전 배포 전 준비 중", serviceName);
        dictionaryService.preDeploy();
        log.info("{} preDeploy 완료", serviceName);
      } catch (Exception e) {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.error("{} 사전 배포 전 준비 실패", serviceName, e);
        throw new RuntimeException(serviceName + " 사전 배포 준비 실패", e);
      }
    }

    log.info("배포 전 사전 준비작업 완료");
  }

  @Transactional
  public void postDeployAll() {
    log.info("배포 후 사전 정리작업 시작");

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.debug("{} 사전 배포 후 정리 중", serviceName);
        dictionaryService.postDeploy();
        log.info("{} postDeploy 완료", serviceName);
      } catch (Exception e) {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.error("{} 사전 배포 후 정리 실패", serviceName, e);
        throw new RuntimeException(serviceName + " 사전 배포 후 정리 실패", e);
      }
    }

    log.info("배포 후 사전 정리작업 완료");
  }

  @Transactional
  public void deployAllToTemp() {
    log.info("모든 사전을 임시 환경으로 배포 시작");

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.info("{} 사전을 임시 환경으로 배포 중", serviceName);
        dictionaryService.deployToTemp();
        log.info("{} 사전 임시 환경 배포 완료", serviceName);
      } catch (Exception e) {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.error("{} 사전 임시 환경 배포 실패", serviceName, e);
        throw new RuntimeException(serviceName + " 사전 임시 환경 배포 실패", e);
      }
    }

    log.info("모든 사전 임시 환경 배포 완료");
  }

  @Transactional
  public void realtimeSyncAll(EnvironmentType environment) {
    log.info("모든 사전을 {} 환경에서 실시간 동기화 시작", environment);

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.debug("{} 사전 실시간 동기화 중", serviceName);
        dictionaryService.realtimeSync(environment);
      } catch (Exception e) {
        throw new RuntimeException("사전 실시간 동기화 실패", e);
      }
    }

    log.info("모든 사전 {} 환경 실시간 동기화 완료", environment);
  }

  @Transactional
  public void deleteAllByEnvironment(EnvironmentType environment) {
    if (environment == EnvironmentType.CURRENT) {
      throw new IllegalArgumentException("CURRENT 환경의 사전은 삭제할 수 없습니다");
    }

    log.info("{} 환경의 모든 사전 삭제 시작", environment);

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        String serviceName = dictionaryService.getClass().getSimpleName();
        dictionaryService.deleteByEnvironmentType(environment);
        log.info("{} 사전 {} 환경 삭제 완료", serviceName, environment);
      } catch (UnsupportedOperationException e) {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.debug("{} 사전은 환경별 삭제를 지원하지 않습니다", serviceName);
      } catch (Exception e) {
        String serviceName = dictionaryService.getClass().getSimpleName();
        log.error("{} 사전 {} 환경 삭제 실패", serviceName, environment, e);
        throw new RuntimeException(serviceName + " 사전 삭제 실패", e);
      }
    }

    log.info("{} 환경의 모든 사전 삭제 완료", environment);
  }
}
