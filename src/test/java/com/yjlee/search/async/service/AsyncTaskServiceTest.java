package com.yjlee.search.async.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yjlee.search.async.dto.AsyncTaskListResponse;
import com.yjlee.search.async.dto.AsyncTaskResponse;
import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.repository.AsyncTaskRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AsyncTaskServiceTest {

  @Autowired private AsyncTaskService asyncTaskService;
  @Autowired private AsyncTaskRepository asyncTaskRepository;

  @BeforeEach
  void setUp() {
    asyncTaskRepository.deleteAll();
  }

  @Test
  @DisplayName("작업 생성 성공")
  void createTaskSuccess() {
    AsyncTask result = asyncTaskService.createTask(AsyncTaskType.INDEXING, "테스트 작업");

    assertThat(result).isNotNull();
    assertThat(result.getId()).isNotNull();
    assertThat(result.getTaskType()).isEqualTo(AsyncTaskType.INDEXING);
    assertThat(result.getStatus()).isEqualTo(AsyncTaskStatus.PENDING);
    assertThat(result.getProgress()).isEqualTo(0);
    assertThat(result.getMessage()).isEqualTo("테스트 작업");
    assertThat(asyncTaskRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("진행률 업데이트 성공")
  void updateProgressSuccess() {
    AsyncTask task = asyncTaskService.createTask(AsyncTaskType.INDEXING, "테스트 작업");
    asyncTaskService.updateProgress(task.getId(), 50, "50% 완료");

    AsyncTask updatedTask = asyncTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(updatedTask.getProgress()).isEqualTo(50);
    assertThat(updatedTask.getMessage()).isEqualTo("50% 완료");
    assertThat(updatedTask.getStatus()).isEqualTo(AsyncTaskStatus.IN_PROGRESS);
    assertThat(updatedTask.getStartedAt()).isNotNull();
  }

  @Test
  @DisplayName("존재하지 않는 작업 업데이트 - 무시")
  void updateProgressTaskNotFound() {
    asyncTaskService.updateProgress(999L, 50, "50% 완료");
    assertThat(asyncTaskRepository.count()).isEqualTo(0);
  }

  @Test
  @DisplayName("작업 정상 완료")
  void completeTaskSuccess() {
    AsyncTask task = asyncTaskService.createTask(AsyncTaskType.EVALUATION_EXECUTION, "평가 작업");
    Map<String, Object> result = Map.of("success", true, "count", 100);
    asyncTaskService.completeTask(task.getId(), result);

    AsyncTask completedTask = asyncTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(completedTask.getStatus()).isEqualTo(AsyncTaskStatus.COMPLETED);
    assertThat(completedTask.getProgress()).isEqualTo(100);
    assertThat(completedTask.getResult()).contains("success");
    assertThat(completedTask.getResult()).contains("100");
    assertThat(completedTask.getCompletedAt()).isNotNull();
  }

  @Test
  @DisplayName("JSON 변환 가능한 객체 완료")
  void completeTaskWithComplexObject() {
    AsyncTask task = asyncTaskService.createTask(AsyncTaskType.LLM_EVALUATION, "LLM 평가");
    Map<String, Object> complexResult = Map.of(
        "queries", List.of("검색어1", "검색어2"),
        "scores", Map.of("precision", 0.85, "recall", 0.92),
        "totalTime", 1234
    );
    asyncTaskService.completeTask(task.getId(), complexResult);

    AsyncTask completedTask = asyncTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(completedTask.getResult()).contains("queries");
    assertThat(completedTask.getResult()).contains("검색어1");
    assertThat(completedTask.getResult()).contains("0.85");
  }

  @Test
  @DisplayName("작업 실패 처리")
  void failTaskSuccess() {
    AsyncTask task = asyncTaskService.createTask(AsyncTaskType.CANDIDATE_GENERATION, "후보군 생성");
    asyncTaskService.failTask(task.getId(), "데이터베이스 연결 실패");

    AsyncTask failedTask = asyncTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(failedTask.getStatus()).isEqualTo(AsyncTaskStatus.FAILED);
    assertThat(failedTask.getErrorMessage()).isEqualTo("데이터베이스 연결 실패");
    assertThat(failedTask.getCompletedAt()).isNotNull();
  }

  @Test
  @DisplayName("ID로 작업 조회 성공")
  void getTaskOrThrowSuccess() {
    AsyncTask task = asyncTaskService.createTask(AsyncTaskType.INDEXING, "색인 작업");
    AsyncTaskResponse response = asyncTaskService.getTaskOrThrow(task.getId());

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(task.getId());
    assertThat(response.getTaskType()).isEqualTo(AsyncTaskType.INDEXING);
    assertThat(response.getStatus()).isEqualTo(AsyncTaskStatus.PENDING);
    assertThat(response.getMessage()).isEqualTo("색인 작업");
  }

  @Test
  @DisplayName("작업 없음 - 예외 발생")
  void getTaskOrThrowNotFound() {
    assertThatThrownBy(() -> asyncTaskService.getTaskOrThrow(999L))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessageContaining("Task not found: 999");
  }

  @Test
  @DisplayName("실행중 작업 필터링")
  void getRunningTasksSuccess() {
    AsyncTask pendingTask = asyncTaskService.createTask(
        AsyncTaskType.INDEXING, "대기중");

    AsyncTask inProgressTask = asyncTaskService.createTask(
        AsyncTaskType.LLM_EVALUATION, "진행중");
    asyncTaskService.updateProgress(inProgressTask.getId(), 50, "처리중");

    AsyncTask completedTask = asyncTaskService.createTask(
        AsyncTaskType.EVALUATION_EXECUTION, "완료");
    asyncTaskService.completeTask(completedTask.getId(), Map.of("done", true));

    AsyncTask failedTask = asyncTaskService.createTask(
        AsyncTaskType.CANDIDATE_GENERATION, "실패");
    asyncTaskService.failTask(failedTask.getId(), "에러");

    List<AsyncTaskResponse> runningTasks = asyncTaskService.getRunningTasks();

    assertThat(runningTasks).hasSize(2);
    assertThat(runningTasks)
        .extracting(AsyncTaskResponse::getId)
        .containsExactlyInAnyOrder(pendingTask.getId(), inProgressTask.getId());
    assertThat(runningTasks)
        .extracting(AsyncTaskResponse::getStatus)
        .containsExactlyInAnyOrder(AsyncTaskStatus.PENDING, AsyncTaskStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("동일 타입 실행중 - true")
  void hasRunningTaskReturnsTrue() {
    asyncTaskService.createTask(AsyncTaskType.INDEXING, "색인 작업");
    boolean result = asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING);
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("동일 타입 실행중 없음 - false")
  void hasRunningTaskReturnsFalse() {
    AsyncTask task = asyncTaskService.createTask(AsyncTaskType.INDEXING, "색인 작업");
    asyncTaskService.completeTask(task.getId(), Map.of("done", true));
    boolean result = asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING);
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("최근 작업 조회 - 페이징")
  void getRecentTasksWithPaging() {
    for (int i = 0; i < 15; i++) {
      AsyncTask task = asyncTaskService.createTask(
          AsyncTaskType.values()[i % 4],
          "작업 " + (i + 1)
      );
      if (i % 3 == 0) {
        asyncTaskService.updateProgress(task.getId(), 50, "진행중");
      }
      if (i % 4 == 0) {
        asyncTaskService.completeTask(task.getId(), Map.of("index", i));
      }
    }

    AsyncTaskListResponse page1 = asyncTaskService.getRecentTasks(0, 5);
    AsyncTaskListResponse page2 = asyncTaskService.getRecentTasks(1, 5);
    AsyncTaskListResponse page3 = asyncTaskService.getRecentTasks(2, 5);

    assertThat(page1.getTasks()).hasSize(5);
    assertThat(page1.getTotalCount()).isEqualTo(15);
    assertThat(page1.getTotalPages()).isEqualTo(3);
    assertThat(page1.getCurrentPage()).isEqualTo(0);
    assertThat(page1.getHasNext()).isTrue();
    assertThat(page1.getHasPrevious()).isFalse();

    assertThat(page2.getTasks()).hasSize(5);
    assertThat(page2.getCurrentPage()).isEqualTo(1);
    assertThat(page2.getHasNext()).isTrue();
    assertThat(page2.getHasPrevious()).isTrue();

    assertThat(page3.getTasks()).hasSize(5);
    assertThat(page3.getCurrentPage()).isEqualTo(2);
    assertThat(page3.getHasNext()).isFalse();
    assertThat(page3.getHasPrevious()).isTrue();
  }

  @Test
  @DisplayName("시나리오: 작업 생성 → 진행 → 완료")
  void scenarioCreateUpdateComplete() {
    AsyncTask task = asyncTaskService.createTask(
        AsyncTaskType.EVALUATION_EXECUTION, "평가 실행 준비");

    assertThat(task.getStatus()).isEqualTo(AsyncTaskStatus.PENDING);
    assertThat(task.getProgress()).isEqualTo(0);

    asyncTaskService.updateProgress(task.getId(), 30, "데이터 로딩 중...");

    AsyncTask progressTask1 = asyncTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(progressTask1.getStatus()).isEqualTo(AsyncTaskStatus.IN_PROGRESS);
    assertThat(progressTask1.getProgress()).isEqualTo(30);

    asyncTaskService.updateProgress(task.getId(), 70, "평가 진행 중...");

    AsyncTask progressTask2 = asyncTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(progressTask2.getProgress()).isEqualTo(70);

    Map<String, Object> result = Map.of(
        "totalQueries", 100,
        "successCount", 95,
        "failCount", 5
    );

    asyncTaskService.completeTask(task.getId(), result);

    AsyncTask completedTask = asyncTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(completedTask.getStatus()).isEqualTo(AsyncTaskStatus.COMPLETED);
    assertThat(completedTask.getProgress()).isEqualTo(100);
    assertThat(completedTask.getResult()).contains("totalQueries");
  }

  @Test
  @DisplayName("시나리오: 작업 생성 → 진행 → 실패")
  void scenarioCreateAndFail() {
    AsyncTask task = asyncTaskService.createTask(
        AsyncTaskType.CANDIDATE_GENERATION, "후보군 생성 시작");

    asyncTaskService.updateProgress(task.getId(), 40, "검색 쿼리 처리 중...");

    AsyncTask progressTask = asyncTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(progressTask.getStatus()).isEqualTo(AsyncTaskStatus.IN_PROGRESS);

    asyncTaskService.failTask(task.getId(), "Elasticsearch 연결 실패: Connection timeout");

    AsyncTask failedTask = asyncTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(failedTask.getStatus()).isEqualTo(AsyncTaskStatus.FAILED);
    assertThat(failedTask.getErrorMessage()).contains("Connection timeout");
    assertThat(failedTask.getProgress()).isEqualTo(40);
  }

  @Test
  @DisplayName("시나리오: 여러 작업 실행중 필터")
  void scenarioMultipleTasksFilterRunning() {
    AsyncTask completedTask = asyncTaskService.createTask(
        AsyncTaskType.INDEXING, "완료된 작업");
    asyncTaskService.completeTask(completedTask.getId(), Map.of("done", true));

    AsyncTask pendingTask = asyncTaskService.createTask(
        AsyncTaskType.LLM_EVALUATION, "대기중 작업");

    AsyncTask inProgressTask = asyncTaskService.createTask(
        AsyncTaskType.EVALUATION_EXECUTION, "진행중 작업");
    asyncTaskService.updateProgress(inProgressTask.getId(), 60, "처리중");

    AsyncTask failedTask = asyncTaskService.createTask(
        AsyncTaskType.CANDIDATE_GENERATION, "실패한 작업");
    asyncTaskService.failTask(failedTask.getId(), "에러 발생");

    List<AsyncTaskResponse> runningTasks = asyncTaskService.getRunningTasks();

    assertThat(runningTasks).hasSize(2);
    assertThat(runningTasks)
        .extracting(AsyncTaskResponse::getId)
        .containsExactlyInAnyOrder(pendingTask.getId(), inProgressTask.getId());
    assertThat(runningTasks)
        .extracting(AsyncTaskResponse::getStatus)
        .containsExactlyInAnyOrder(AsyncTaskStatus.PENDING, AsyncTaskStatus.IN_PROGRESS);
    assertThat(asyncTaskRepository.count()).isEqualTo(4);
  }
}