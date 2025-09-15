package com.yjlee.search.async.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.async.dto.AsyncTaskListResponse;
import com.yjlee.search.async.dto.AsyncTaskResponse;
import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.repository.AsyncTaskRepository;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AsyncTaskServiceTest {

  @Mock private AsyncTaskRepository asyncTaskRepository;
  @Mock private ObjectMapper objectMapper;
  @InjectMocks private AsyncTaskService asyncTaskService;

  private AsyncTask testTask;

  @BeforeEach
  void setUp() {
    testTask =
        AsyncTask.builder()
            .id(1L)
            .taskType(AsyncTaskType.INDEXING)
            .status(AsyncTaskStatus.PENDING)
            .progress(0)
            .message("테스트 작업")
            .build();
  }

  @Test
  @DisplayName("파라미터 포함 작업 생성")
  void createTaskWithParams() throws Exception {
    Map<String, Object> params = Map.of("key", "value");
    when(objectMapper.writeValueAsString(params)).thenReturn("{\"key\":\"value\"}");
    when(asyncTaskRepository.save(any(AsyncTask.class))).thenReturn(testTask);

    AsyncTask result = asyncTaskService.createTask(AsyncTaskType.INDEXING, "테스트 작업", params);

    assertThat(result).isNotNull();
    verify(objectMapper).writeValueAsString(params);
    verify(asyncTaskRepository).save(any(AsyncTask.class));
  }

  @Test
  @DisplayName("중복 실행 방지")
  void createTaskIfNotRunningThrowsException() {
    when(asyncTaskRepository.existsByTaskTypeAndStatusInForUpdate(anyString(), anyList()))
        .thenReturn(true);

    assertThatThrownBy(
            () -> asyncTaskService.createTaskIfNotRunning(AsyncTaskType.INDEXING, "새 작업", null))
        .isInstanceOf(IllegalStateException.class);

    verify(asyncTaskRepository).existsByTaskTypeAndStatusInForUpdate(anyString(), anyList());
    verify(asyncTaskRepository, never()).save(any());
  }

  @Test
  @DisplayName("진행률 업데이트")
  void updateProgress() {
    when(asyncTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
    when(asyncTaskRepository.save(any(AsyncTask.class))).thenReturn(testTask);

    asyncTaskService.updateProgress(1L, 50, "진행 중");

    assertThat(testTask.getProgress()).isEqualTo(50);
    assertThat(testTask.getMessage()).isEqualTo("진행 중");
    verify(asyncTaskRepository).findById(1L);
    verify(asyncTaskRepository).save(testTask);
  }

  @Test
  @DisplayName("작업 완료 처리")
  void completeTask() throws Exception {
    when(asyncTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
    when(objectMapper.writeValueAsString(any())).thenReturn("{\"result\":\"success\"}");
    when(asyncTaskRepository.save(any(AsyncTask.class))).thenReturn(testTask);

    asyncTaskService.completeTask(1L, Map.of("result", "success"));

    assertThat(testTask.getStatus()).isEqualTo(AsyncTaskStatus.COMPLETED);
    assertThat(testTask.getProgress()).isEqualTo(100);
    verify(asyncTaskRepository).findById(1L);
    verify(asyncTaskRepository).save(testTask);
  }

  @Test
  @DisplayName("작업 실패 처리")
  void failTask() {
    when(asyncTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));
    when(asyncTaskRepository.save(any(AsyncTask.class))).thenReturn(testTask);

    asyncTaskService.failTask(1L, "에러 발생");

    assertThat(testTask.getStatus()).isEqualTo(AsyncTaskStatus.FAILED);
    assertThat(testTask.getErrorMessage()).isEqualTo("에러 발생");
    verify(asyncTaskRepository).findById(1L);
    verify(asyncTaskRepository).save(testTask);
  }

  @Test
  @DisplayName("작업 조회 성공")
  void getTaskOrThrowSuccess() {
    when(asyncTaskRepository.findById(1L)).thenReturn(Optional.of(testTask));

    AsyncTaskResponse result = asyncTaskService.getTaskOrThrow(1L);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getTaskType()).isEqualTo(AsyncTaskType.INDEXING);
  }

  @Test
  @DisplayName("존재하지 않는 작업 조회시 예외 발생")
  void getTaskOrThrowNotFound() {
    when(asyncTaskRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> asyncTaskService.getTaskOrThrow(1L))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("작업 목록 조회")
  void getRecentTasks() {
    List<AsyncTask> tasks = List.of(testTask);
    Page<AsyncTask> page = new PageImpl<>(tasks, PageRequest.of(0, 10), 1);
    when(asyncTaskRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
        .thenReturn(page);

    AsyncTaskListResponse result = asyncTaskService.getRecentTasks(0, 10);

    assertThat(result).isNotNull();
    assertThat(result.getTasks()).hasSize(1);
    assertThat(result.getTotalCount()).isEqualTo(1);
    assertThat(result.getCurrentPage()).isEqualTo(0);
    verify(asyncTaskRepository).findAllByOrderByCreatedAtDesc(any(PageRequest.class));
  }

  @Test
  @DisplayName("실행 중인 작업 목록 조회")
  void getRunningTasks() {
    AsyncTask runningTask =
        AsyncTask.builder()
            .id(2L)
            .taskType(AsyncTaskType.LLM_EVALUATION)
            .status(AsyncTaskStatus.IN_PROGRESS)
            .progress(30)
            .message("실행 중")
            .build();

    when(asyncTaskRepository.findByStatusInOrderByCreatedAtDesc(anyList()))
        .thenReturn(List.of(testTask, runningTask));

    List<AsyncTaskResponse> result = asyncTaskService.getRunningTasks();

    assertThat(result).hasSize(2);
    verify(asyncTaskRepository).findByStatusInOrderByCreatedAtDesc(anyList());
  }

  @Test
  @DisplayName("실행 중인 작업 존재 확인")
  void hasRunningTaskReturnsTrue() {
    when(asyncTaskRepository.existsByTaskTypeAndStatusIn(any(AsyncTaskType.class), anyList()))
        .thenReturn(true);

    boolean result = asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING);

    assertThat(result).isTrue();
    verify(asyncTaskRepository).existsByTaskTypeAndStatusIn(any(AsyncTaskType.class), anyList());
  }
}
