package com.yjlee.search.async.service;

import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.repository.AsyncTaskRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class AsyncTaskExecutor {

  private final AsyncTaskRepository asyncTaskRepository;
  private final List<TaskWorker> taskWorkers;
  @Qualifier("asyncThreadPoolExecutor")
  private final ThreadPoolTaskExecutor executor;
  private Map<String, TaskWorker> workerMap;

  @PostConstruct
  public void init() {
    workerMap = taskWorkers.stream()
        .collect(Collectors.toMap(
            worker -> worker.getSupportedTaskType().name(),
            worker -> worker));

    log.info("AsyncTaskExecutor 초기화 - 풀 크기: {}/{}",
        executor.getCorePoolSize(), executor.getMaxPoolSize());
  }

  @Scheduled(fixedDelay = 5000)
  @Transactional
  public void processPendingTasks() {
    int activeCount = executor.getActiveCount();
    int availableSlots = executor.getMaxPoolSize() - activeCount;

    if (availableSlots <= 0) {
      log.debug("Worker 풀이 가득 참 - {}/{}", activeCount, executor.getMaxPoolSize());
      return;
    }

    List<AsyncTask> pendingTasks = asyncTaskRepository
        .findByStatusOrderByCreatedAtAsc(AsyncTaskStatus.PENDING);

    if (pendingTasks.isEmpty()) {
      return;
    }

    int tasksToProcess = Math.min(pendingTasks.size(), availableSlots);

    for (int i = 0; i < tasksToProcess; i++) {
      AsyncTask task = pendingTasks.get(i);
      processTask(task);
    }
  }

  private void processTask(AsyncTask task) {
    try {
      task.updateProgress(0, "작업 시작");
      asyncTaskRepository.save(task);

      TaskWorker worker = workerMap.get(task.getTaskType().name());
      if (worker == null) {
        log.error("작업 타입 {}에 대한 워커를 찾을 수 없음", task.getTaskType());
        task.fail("지원하지 않는 작업 타입: " + task.getTaskType());
        asyncTaskRepository.save(task);
        return;
      }

      executor.execute(() -> {
        log.info("작업 실행 시작: ID={}, 타입={}", task.getId(), task.getTaskType());
        try {
          worker.execute(task);
        } catch (Exception e) {
          log.error("작업 실행 중 오류 발생: ID={}", task.getId(), e);
        }
      });

    } catch (Exception e) {
      log.error("작업 처리 중 오류 발생: ID={}", task.getId(), e);
    }
  }

  public int getActiveTaskCount() {
    return executor.getActiveCount();
  }

  public int getQueuedTaskCount() {
    return executor.getThreadPoolExecutor().getQueue().size();
  }

  public int getCompletedTaskCount() {
    return (int) executor.getThreadPoolExecutor().getCompletedTaskCount();
  }
}