package com.yjlee.search.async.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.async.dto.AsyncTaskListResponse;
import com.yjlee.search.async.dto.AsyncTaskResponse;
import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.repository.AsyncTaskRepository;
import com.yjlee.search.evaluation.util.PaginationUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

  @Transactional
  public AsyncTask createTask(AsyncTaskType taskType, String initialMessage) {
    AsyncTask task = AsyncTask.builder()
        .taskType(taskType)
        .status(AsyncTaskStatus.PENDING)
        .progress(0)
        .message(initialMessage)
        .build();

    AsyncTask savedTask = asyncTaskRepository.save(task);
    log.info("비동기 작업 생성: ID={}, 타입={}", savedTask.getId(), taskType);
    return savedTask;
  }

  @Transactional
  public void updateProgress(Long taskId, int progress, String message) {
    executeOnTask(taskId, task -> {
      task.updateProgress(progress, message);
      log.debug("작업 진행률 업데이트: ID={}, 진행률={}%, 메시지={}", taskId, progress, message);
    });
  }

  @Transactional
  public void completeTask(Long taskId, Object result) {
    executeOnTask(taskId, task -> {
      try {
        String resultJson = objectMapper.writeValueAsString(result);
        task.complete(resultJson);
      } catch (Exception e) {
        task.complete(result.toString());
      }
      log.info("작업 완료: ID={}, 타입={}", taskId, task.getTaskType());
    });
  }

  @Transactional
  public void failTask(Long taskId, String errorMessage) {
    executeOnTask(taskId, task -> {
      task.fail(errorMessage);
      log.error("작업 실패: ID={}, 타입={}, 에러={}", taskId, task.getTaskType(), errorMessage);
    });
  }

  private void executeOnTask(Long taskId, Consumer<AsyncTask> action) {
    asyncTaskRepository.findById(taskId).ifPresent(task -> {
      action.accept(task);
      asyncTaskRepository.save(task);
    });
  }

  public AsyncTaskResponse getTaskOrThrow(Long taskId) {
    return asyncTaskRepository
        .findById(taskId)
        .map(this::convertToResponse)
        .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));
  }

  public AsyncTaskListResponse getRecentTasks(int page, int size) {
    List<AsyncTask> allTasks = asyncTaskRepository
        .findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime.now().minusDays(7));

    PaginationUtils.PagedResult<AsyncTask> pagedResult =
        PaginationUtils.paginate(allTasks, page, size);

    List<AsyncTaskResponse> taskResponses = pagedResult.getContent().stream()
        .map(this::convertToResponse)
        .toList();

    return AsyncTaskListResponse.builder()
        .tasks(taskResponses)
        .totalCount(pagedResult.getTotalCount())
        .totalPages(pagedResult.getTotalPages())
        .currentPage(pagedResult.getCurrentPage())
        .size(pagedResult.getSize())
        .hasNext(pagedResult.isHasNext())
        .hasPrevious(pagedResult.isHasPrevious())
        .build();
  }

  public List<AsyncTaskResponse> getRunningTasks() {
    return asyncTaskRepository.findByStatusInOrderByCreatedAtDesc(RUNNING_STATUSES)
        .stream()
        .map(this::convertToResponse)
        .toList();
  }

  public boolean hasRunningTask(AsyncTaskType taskType) {
    return asyncTaskRepository.existsByTaskTypeAndStatusIn(taskType, RUNNING_STATUSES);
  }

  private AsyncTaskResponse convertToResponse(AsyncTask task) {
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