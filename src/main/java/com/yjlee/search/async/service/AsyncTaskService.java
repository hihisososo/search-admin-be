package com.yjlee.search.async.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.async.dto.AsyncTaskListResponse;
import com.yjlee.search.async.dto.AsyncTaskResponse;
import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.repository.AsyncTaskRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsyncTaskService {

  private static final List<AsyncTaskStatus> RUNNING_STATUSES =
      List.of(AsyncTaskStatus.PENDING, AsyncTaskStatus.IN_PROGRESS);

  private final AsyncTaskRepository asyncTaskRepository;
  private final ObjectMapper objectMapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AsyncTask createTask(AsyncTaskType taskType, String initialMessage) {
    return createTask(taskType, initialMessage, null);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AsyncTask createTask(AsyncTaskType taskType, String initialMessage, Object params) {
    AsyncTask task =
        AsyncTask.builder()
            .taskType(taskType)
            .status(AsyncTaskStatus.PENDING)
            .progress(0)
            .message(initialMessage)
            .params(params != null ? toJson(params) : null)
            .build();

    AsyncTask savedTask = asyncTaskRepository.save(task);
    log.info("비동기 작업 생성: ID={}, 타입={}", savedTask.getId(), taskType);
    return savedTask;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AsyncTask createTaskWithParams(
      AsyncTaskType taskType, String initialMessage, Object params) {
    return createTask(taskType, initialMessage, params);
  }

  @Transactional
  public synchronized AsyncTask createTaskIfNotRunning(
      AsyncTaskType taskType, String initialMessage, Object params) {
    List<String> runningStatuses =
        RUNNING_STATUSES.stream().map(Enum::name).collect(Collectors.toList());

    if (asyncTaskRepository.existsByTaskTypeAndStatusInForUpdate(
        taskType.name(), runningStatuses)) {
      throw new IllegalStateException("작업이 이미 진행 중입니다: " + taskType.getDisplayName());
    }

    return createTask(taskType, initialMessage, params);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateProgress(Long taskId, int progress, String message) {
    asyncTaskRepository
        .findById(taskId)
        .ifPresent(
            task -> {
              task.updateProgress(progress, message);
              asyncTaskRepository.save(task);
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeTask(Long taskId, Object result) {
    asyncTaskRepository
        .findById(taskId)
        .ifPresent(
            task -> {
              task.complete(result != null ? toJson(result) : null);
              asyncTaskRepository.save(task);
              log.info("작업 완료: ID={}, 타입={}", taskId, task.getTaskType());
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failTask(Long taskId, String errorMessage) {
    asyncTaskRepository
        .findById(taskId)
        .ifPresent(
            task -> {
              task.fail(errorMessage);
              asyncTaskRepository.save(task);
              log.error("작업 실패: ID={}, 타입={}", taskId, task.getTaskType());
            });
  }

  public AsyncTaskResponse getTaskOrThrow(Long taskId) {
    return asyncTaskRepository
        .findById(taskId)
        .map(this::toResponse)
        .orElseThrow(() -> new NoSuchElementException("Task 를 찾을 수 없습니다: " + taskId));
  }

  public AsyncTaskListResponse getRecentTasks(Pageable pageable) {
    Page<AsyncTask> taskPage =
        asyncTaskRepository.findAllByOrderByCreatedAtDesc(pageable);

    return AsyncTaskListResponse.builder()
        .tasks(taskPage.map(this::toResponse).getContent())
        .totalCount(taskPage.getTotalElements())
        .totalPages(taskPage.getTotalPages())
        .currentPage(pageable.getPageNumber())
        .size(pageable.getPageSize())
        .hasNext(taskPage.hasNext())
        .hasPrevious(taskPage.hasPrevious())
        .build();
  }

  public List<AsyncTaskResponse> getRunningTasks() {
    return asyncTaskRepository.findByStatusInOrderByCreatedAtDesc(RUNNING_STATUSES).stream()
        .map(this::toResponse)
        .toList();
  }

  public boolean hasRunningTask(AsyncTaskType taskType) {
    return asyncTaskRepository.existsByTaskTypeAndStatusIn(taskType, RUNNING_STATUSES);
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      log.error("JSON 직렬화 실패", e);
      return obj != null ? obj.toString() : "{}";
    }
  }

  private AsyncTaskResponse toResponse(AsyncTask task) {
    return AsyncTaskResponse.builder()
        .id(task.getId())
        .taskType(task.getTaskType())
        .status(task.getStatus())
        .progress(task.getProgress())
        .message(task.getMessage())
        .errorMessage(task.getErrorMessage())
        .result(task.getResult())
        .createdAt(task.getCreatedAt())
        .startedAt(task.getStartedAt())
        .completedAt(task.getCompletedAt())
        .build();
  }
}
