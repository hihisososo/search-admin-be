package com.yjlee.search.common.service;

import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.repository.AsyncTaskRepository;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.service.IndexEnvironmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartupListener {

  private final AsyncTaskRepository asyncTaskRepository;
  private final IndexEnvironmentService environmentService;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void handleApplicationReady() {
    // IndexEnvironment 초기화
    initializeEnvironments();

    // 진행 중이었던 Task 정리
    log.info("진행 중이었던 Task 정리 시작");

    var inProgressTasks = asyncTaskRepository.findByStatus(AsyncTaskStatus.IN_PROGRESS);

    if (!inProgressTasks.isEmpty()) {
      inProgressTasks.forEach(
          task -> {
            task.fail("애플리케이션 재시작으로 인한 작업 실패");
          });
      asyncTaskRepository.saveAll(inProgressTasks);
      log.warn("진행 중이던 {}개의 작업을 실패 처리", inProgressTasks.size());
      return;
    }
    log.info("진행 중이었던 Task 정리 완료");
  }

  private void initializeEnvironments() {
    // DEV 환경이 없으면 생성
    if (!environmentService.existsEnvironment(EnvironmentType.DEV)) {
      environmentService.createEnvironment(EnvironmentType.DEV);
      log.info("DEV 환경 초기화 완료");
    }

    // PROD 환경도 미리 생성 (선택사항)
    if (!environmentService.existsEnvironment(EnvironmentType.PROD)) {
      environmentService.createEnvironment(EnvironmentType.PROD);
      log.info("PROD 환경 초기화 완료");
    }
  }
}
