package com.yjlee.search.dictionary.common.service;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
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
  public void deployAllToDev() {
    log.info("모든 사전을 개발 환경으로 배포 시작");

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        log.info("{} 사전을 개발 환경으로 배포 중", dictionaryService.getDictionaryTypeEnum());
        dictionaryService.deployToDev();
        log.info("{} 사전 개발 환경 배포 완료", dictionaryService.getDictionaryTypeEnum());
      } catch (Exception e) {
        log.error("{} 사전 개발 환경 배포 실패", dictionaryService.getDictionaryTypeEnum(), e);
        throw new RuntimeException(
            dictionaryService.getDictionaryTypeEnum() + " 사전 개발 환경 배포 실패", e);
      }
    }

    log.info("모든 사전 개발 환경 배포 완료");
  }

  @Transactional
  public void deployAllToProd() {
    log.info("모든 사전을 운영 환경으로 배포 시작");

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        log.info("{} 사전을 운영 환경으로 배포 중", dictionaryService.getDictionaryTypeEnum());
        dictionaryService.deployToProd();
        log.info("{} 사전 운영 환경 배포 완료", dictionaryService.getDictionaryTypeEnum());
      } catch (Exception e) {
        log.error("{} 사전 운영 환경 배포 실패", dictionaryService.getDictionaryTypeEnum(), e);
        throw new RuntimeException(
            dictionaryService.getDictionaryTypeEnum() + " 사전 운영 환경 배포 실패", e);
      }
    }

    log.info("모든 사전 운영 환경 배포 완료");
  }

  @Transactional
  public void realtimeSyncAll(DictionaryEnvironmentType environment) {
    log.info("모든 사전을 {} 환경에서 실시간 동기화 시작", environment);

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        log.debug("{} 사전 실시간 동기화 중", dictionaryService.getDictionaryTypeEnum());
        dictionaryService.realtimeSync(environment);
      } catch (Exception e) {
        log.warn("{} 사전 실시간 동기화 실패 (무시하고 계속)", dictionaryService.getDictionaryTypeEnum(), e);
      }
    }

    log.info("모든 사전 {} 환경 실시간 동기화 완료", environment);
  }

  public String getAllDictionaryContent(DictionaryEnvironmentType environment) {
    StringBuilder content = new StringBuilder();

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        String dictContent = dictionaryService.getDictionaryContent(environment);
        if (dictContent != null && !dictContent.isEmpty()) {
          content
              .append("# ")
              .append(dictionaryService.getDictionaryTypeEnum())
              .append(" Dictionary\n");
          content.append(dictContent).append("\n\n");
        }
      } catch (Exception e) {
        log.warn("{} 사전 콘텐츠 조회 실패", dictionaryService.getDictionaryTypeEnum(), e);
      }
    }

    return content.toString();
  }

  @Transactional
  public void deleteAllByEnvironment(DictionaryEnvironmentType environment) {
    if (environment == DictionaryEnvironmentType.CURRENT) {
      throw new IllegalArgumentException("CURRENT 환경의 사전은 삭제할 수 없습니다");
    }

    log.info("{} 환경의 모든 사전 삭제 시작", environment);

    for (DictionaryService dictionaryService : dictionaryServices) {
      try {
        if (dictionaryService instanceof AbstractDictionaryService) {
          ((AbstractDictionaryService) dictionaryService).deleteByEnvironmentType(environment);
        } else {
          log.debug("{} 사전은 환경별 삭제를 지원하지 않습니다", dictionaryService.getDictionaryTypeEnum());
        }
      } catch (Exception e) {
        log.error("{} 사전 {} 환경 삭제 실패", dictionaryService.getDictionaryTypeEnum(), environment, e);
        throw new RuntimeException(dictionaryService.getDictionaryTypeEnum() + " 사전 삭제 실패", e);
      }
    }

    log.info("{} 환경의 모든 사전 삭제 완료", environment);
  }
}
