package com.yjlee.search.async.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.repository.AsyncTaskRepository;
import com.yjlee.search.test.base.BaseIntegrationTest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AsyncTaskIntegrationTest extends BaseIntegrationTest {

  @Autowired private AsyncTaskRepository asyncTaskRepository;

  @BeforeEach
  void setUp() {
    asyncTaskRepository.deleteAll();
  }

  @Test
  @DisplayName("작업 상태 조회 성공")
  void getTaskStatusSuccess() throws Exception {
    AsyncTask task =
        asyncTaskRepository.save(
            AsyncTask.builder()
                .taskType(AsyncTaskType.INDEXING)
                .status(AsyncTaskStatus.PENDING)
                .progress(0)
                .message("테스트 작업")
                .createdAt(LocalDateTime.now())
                .build());

    mockMvc
        .perform(get("/api/v1/tasks/{taskId}", task.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(task.getId()))
        .andExpect(jsonPath("$.taskType").value("INDEXING"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.progress").value(0))
        .andExpect(jsonPath("$.message").value("테스트 작업"));
  }

  @Test
  @DisplayName("존재하지 않는 작업 조회 시 404")
  void getTaskNotFound() throws Exception {
    mockMvc.perform(get("/api/v1/tasks/{taskId}", 99999L)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("최근 작업 목록 조회")
  void getRecentTasks() throws Exception {
    List<AsyncTask> tasks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      tasks.add(
          asyncTaskRepository.save(
              AsyncTask.builder()
                  .taskType(AsyncTaskType.INDEXING)
                  .status(AsyncTaskStatus.PENDING)
                  .progress(i * 20)
                  .message("작업 " + i)
                  .createdAt(LocalDateTime.now().minusHours(i))
                  .build()));
    }

    mockMvc
        .perform(get("/api/v1/tasks").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tasks").isArray())
        .andExpect(jsonPath("$.tasks.length()").value(5))
        .andExpect(jsonPath("$.totalCount").value(5))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.currentPage").value(0));
  }

  @Test
  @DisplayName("실행 중인 작업 조회")
  void getRunningTasks() throws Exception {
    asyncTaskRepository.save(
        AsyncTask.builder()
            .taskType(AsyncTaskType.INDEXING)
            .status(AsyncTaskStatus.IN_PROGRESS)
            .progress(50)
            .message("실행 중인 작업 1")
            .createdAt(LocalDateTime.now())
            .build());

    asyncTaskRepository.save(
        AsyncTask.builder()
            .taskType(AsyncTaskType.LLM_EVALUATION)
            .status(AsyncTaskStatus.IN_PROGRESS)
            .progress(30)
            .message("실행 중인 작업 2")
            .createdAt(LocalDateTime.now())
            .build());

    asyncTaskRepository.save(
        AsyncTask.builder()
            .taskType(AsyncTaskType.EVALUATION_EXECUTION)
            .status(AsyncTaskStatus.COMPLETED)
            .progress(100)
            .message("완료된 작업")
            .createdAt(LocalDateTime.now())
            .build());

    mockMvc
        .perform(get("/api/v1/tasks/running"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$[1].status").value("IN_PROGRESS"));
  }

  @Test
  @DisplayName("페이징 파라미터 검증")
  void getTasksWithPaging() throws Exception {
    for (int i = 0; i < 25; i++) {
      asyncTaskRepository.save(
          AsyncTask.builder()
              .taskType(AsyncTaskType.INDEXING)
              .status(AsyncTaskStatus.COMPLETED)
              .progress(100)
              .message("작업 " + i)
              .createdAt(LocalDateTime.now().minusDays(i % 6))
              .build());
    }

    mockMvc
        .perform(get("/api/v1/tasks").param("page", "1").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tasks.length()").value(10))
        .andExpect(jsonPath("$.currentPage").value(1))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.totalCount").value(25));
  }
}