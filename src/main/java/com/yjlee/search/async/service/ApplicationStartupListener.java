package com.yjlee.search.async.service;

import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.repository.AsyncTaskRepository;
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

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void handleApplicationReady() {
    log.info("진행 중이었던 Task 정리");

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
}
