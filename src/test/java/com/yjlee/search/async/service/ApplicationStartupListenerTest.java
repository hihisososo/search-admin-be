package com.yjlee.search.async.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.repository.AsyncTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationStartupListenerTest {

  @Mock private AsyncTaskRepository asyncTaskRepository;
  @InjectMocks private ApplicationStartupListener applicationStartupListener;

  @Test
  @DisplayName("애플리케이션 시작시 진행 중인 작업 실패 처리")
  void handleInProgressTasksOnStartup() {
    AsyncTask task1 = AsyncTask.builder()
        .id(1L)
        .taskType(AsyncTaskType.INDEXING)
        .status(AsyncTaskStatus.IN_PROGRESS)
        .progress(50)
        .message("진행 중인 작업 1")
        .createdAt(LocalDateTime.now())
        .build();

    AsyncTask task2 = AsyncTask.builder()
        .id(2L)
        .taskType(AsyncTaskType.LLM_EVALUATION)
        .status(AsyncTaskStatus.IN_PROGRESS)
        .progress(30)
        .message("진행 중인 작업 2")
        .createdAt(LocalDateTime.now())
        .build();

    List<AsyncTask> inProgressTasks = List.of(task1, task2);
    when(asyncTaskRepository.findByStatus(AsyncTaskStatus.IN_PROGRESS))
        .thenReturn(inProgressTasks);

    applicationStartupListener.handleApplicationReady();

    assertThat(task1.getStatus()).isEqualTo(AsyncTaskStatus.FAILED);
    assertThat(task2.getStatus()).isEqualTo(AsyncTaskStatus.FAILED);

    verify(asyncTaskRepository).findByStatus(AsyncTaskStatus.IN_PROGRESS);
    verify(asyncTaskRepository).saveAll(inProgressTasks);
  }
}