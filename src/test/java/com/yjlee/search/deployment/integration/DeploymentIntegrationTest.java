package com.yjlee.search.deployment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yjlee.search.async.model.AsyncTask;
import com.yjlee.search.async.model.AsyncTaskStatus;
import com.yjlee.search.async.repository.AsyncTaskRepository;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.enums.DeploymentStatus;
import com.yjlee.search.deployment.enums.DeploymentType;
import com.yjlee.search.deployment.enums.IndexStatus;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.repository.ProductRepository;
import com.yjlee.search.test.base.BaseIntegrationTest;
import com.yjlee.search.test.util.TestDataLoader;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class DeploymentIntegrationTest extends BaseIntegrationTest {

  @Autowired private IndexEnvironmentRepository environmentRepository;
  @Autowired private DeploymentHistoryRepository historyRepository;
  @Autowired private AsyncTaskRepository asyncTaskRepository;
  @Autowired private ProductRepository productRepository;

  private TestDataLoader testDataLoader;

  @BeforeEach
  void setUp() throws Exception {
    deleteAllTestIndices();

    testDataLoader = new TestDataLoader(objectMapper);

    environmentRepository.deleteAll();
    historyRepository.deleteAll();
    asyncTaskRepository.deleteAll();
    productRepository.deleteAll();

    environmentRepository.save(
        IndexEnvironment.builder()
            .environmentType(EnvironmentType.DEV)
            .indexStatus(IndexStatus.INACTIVE)
            .build());

    environmentRepository.save(
        IndexEnvironment.builder()
            .environmentType(EnvironmentType.PROD)
            .indexStatus(IndexStatus.INACTIVE)
            .build());

    createTestProducts();
  }

  @Test
  @DisplayName("환경 목록 조회")
  void getEnvironments() throws Exception {
    mockMvc
        .perform(get("/api/v1/deployment/environments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.environments").isArray())
        .andExpect(jsonPath("$.environments.length()").value(2))
        .andExpect(jsonPath("$.environments[0].environmentType").value("DEV"))
        .andExpect(jsonPath("$.environments[1].environmentType").value("PROD"));
  }

  @Test
  @DisplayName("색인 실행")
  void executeIndexing() throws Exception {
    Long taskId = executeIndexingRequest("테스트 색인");
    waitForAsyncTask(taskId, 30);
    verifyIndexingSuccess(taskId, 10L);
  }

  @Test
  @DisplayName("색인 중복 실행 방지")
  void preventDuplicateIndexing() throws Exception {

    Long firstTaskId = executeIndexingRequest("첫 번째 색인");
    Thread.sleep(500);

    mockMvc
        .perform(
            post("/api/v1/deployment/indexing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"중복 색인\"}"))
        .andExpect(status().isConflict());

    waitForAsyncTask(firstTaskId, 30);
  }

  @Test
  @DisplayName("색인 후 배포")
  void executeIndexingAndDeployment() throws Exception {

    Long taskId = executeIndexingRequest("배포용 색인");
    waitForAsyncTask(taskId, 30);
    verifyIndexingSuccess(taskId, 10L);

    mockMvc
        .perform(
            post("/api/v1/deployment/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"테스트 배포\"}"))
        .andExpect(status().isOk());

    verifyDeploymentSuccess();
  }

  @Test
  @DisplayName("배포 - 활성 색인 없을 때 실패")
  void deployWithoutActiveIndex() throws Exception {

    String requestBody = "{\"description\": \"테스트 배포\"}";

    mockMvc
        .perform(
            post("/api/v1/deployment/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("배포 이력 조회")
  void getDeploymentHistory() throws Exception {

    for (int i = 0; i < 5; i++) {
      historyRepository.save(
          DeploymentHistory.builder()
              .deploymentType(DeploymentType.INDEXING)
              .status(DeploymentStatus.SUCCESS)
              .version("v2024010112000" + i)
              .documentCount(1000L + i * 100)
              .build());
    }

    mockMvc
        .perform(get("/api/v1/deployment/history").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deploymentHistories").isArray())
        .andExpect(jsonPath("$.deploymentHistories.length()").value(5))
        .andExpect(jsonPath("$.pagination.totalElements").value(5));
  }

  @Test
  @DisplayName("동시 색인 요청")
  void executeConcurrentIndexing() throws Exception {

    List<CompletableFuture<Integer>> futures =
        List.of(
            CompletableFuture.supplyAsync(() -> sendIndexingRequest()),
            CompletableFuture.supplyAsync(() -> sendIndexingRequest()),
            CompletableFuture.supplyAsync(() -> sendIndexingRequest()));

    List<Integer> results = futures.stream().map(CompletableFuture::join).toList();

    assertThat(results.stream().filter(r -> r == 200).count()).isEqualTo(1);
    assertThat(results.stream().filter(r -> r == 409).count()).isEqualTo(2);
    assertThat(asyncTaskRepository.count()).isEqualTo(1);

    asyncTaskRepository.findAll().forEach(task -> waitForAsyncTask(task.getId(), 30));
  }

  @Test
  @DisplayName("연속 배포 시나리오")
  void executeSequentialDeployment() throws Exception {
    Long firstTaskId = executeIndexingRequest("첫 번째 색인");
    waitForAsyncTask(firstTaskId, 30);
    verifyIndexingSuccess(firstTaskId, 10L);

    mockMvc
        .perform(
            post("/api/v1/deployment/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"첫 번째 배포\"}"))
        .andExpect(status().isOk());

    IndexEnvironment prodEnv =
        environmentRepository.findByEnvironmentType(EnvironmentType.PROD).orElseThrow();
    String firstVersion = prodEnv.getVersion();
    assertThat(prodEnv.getIndexStatus()).isEqualTo(IndexStatus.ACTIVE);

    Long secondTaskId = executeIndexingRequest("두 번째 색인");
    waitForAsyncTask(secondTaskId, 30);
    verifyIndexingSuccess(secondTaskId, 10L);

    mockMvc
        .perform(
            post("/api/v1/deployment/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"두 번째 배포\"}"))
        .andExpect(status().isOk());

    prodEnv = environmentRepository.findByEnvironmentType(EnvironmentType.PROD).orElseThrow();
    assertThat(prodEnv.getVersion()).isNotEqualTo(firstVersion);
    assertThat(prodEnv.getIndexStatus()).isEqualTo(IndexStatus.ACTIVE);
  }

  @Test
  @DisplayName("색인 진행 중 배포 차단")
  void blockDeploymentDuringIndexing() throws Exception {
    String requestBody = "{\"description\":\"진행 중 색인\"}";
    mockMvc
        .perform(
            post("/api/v1/deployment/indexing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk());

    Thread.sleep(500);
    mockMvc
        .perform(
            post("/api/v1/deployment/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"차단될 배포\"}"))
        .andExpect(status().isConflict());

    AsyncTask runningTask = asyncTaskRepository.findAll().get(0);
    waitForAsyncTask(runningTask.getId(), 30);
  }

  @Test
  @DisplayName("배포 이력 정렬 및 페이징")
  void paginateDeploymentHistory() throws Exception {
    for (int i = 0; i < 15; i++) {
      historyRepository.save(
          DeploymentHistory.builder()
              .deploymentType(i % 2 == 0 ? DeploymentType.INDEXING : DeploymentType.DEPLOYMENT)
              .status(DeploymentStatus.SUCCESS)
              .version(String.format("v20240101%02d0000", i))
              .documentCount(1000L + i * 100)
              .description("테스트 " + i)
              .build());
    }

    mockMvc
        .perform(get("/api/v1/deployment/history").param("page", "0").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deploymentHistories").isArray())
        .andExpect(jsonPath("$.deploymentHistories.length()").value(10))
        .andExpect(jsonPath("$.pagination.totalElements").value(15))
        .andExpect(jsonPath("$.pagination.totalPages").value(2));

    mockMvc
        .perform(get("/api/v1/deployment/history").param("page", "1").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deploymentHistories.length()").value(5));
  }

  @Test
  @DisplayName("대용량 데이터 색인 테스트")
  void executeLargeDatasetIndexing() throws Exception {
    productRepository.deleteAll();
    List<Product> products = testDataLoader.loadProducts();
    productRepository.saveAll(products);

    Long taskId = executeIndexingRequest("대용량 색인 테스트");
    waitForAsyncTask(taskId, 60);

    AsyncTask task = asyncTaskRepository.findById(taskId).orElseThrow();
    assertThat(task.getStatus()).isEqualTo(AsyncTaskStatus.COMPLETED);

    IndexEnvironment devEnv =
        environmentRepository.findByEnvironmentType(EnvironmentType.DEV).orElseThrow();
    assertThat(devEnv.getIndexStatus()).isEqualTo(IndexStatus.ACTIVE);
    assertThat(devEnv.getDocumentCount()).isEqualTo((long) products.size());
  }

  @Test
  @DisplayName("환경 정보 업데이트 확인")
  void verifyEnvironmentUpdates() throws Exception {
    mockMvc
        .perform(get("/api/v1/deployment/environments"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.environments[?(@.environmentType=='DEV')].indexStatus").value("INACTIVE"))
        .andExpect(
            jsonPath("$.environments[?(@.environmentType=='PROD')].indexStatus").value("INACTIVE"));

    Long taskId = executeIndexingRequest("환경 업데이트 테스트");
    waitForAsyncTask(taskId, 30);
    verifyIndexingSuccess(taskId, 10L);

    mockMvc
        .perform(get("/api/v1/deployment/environments"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.environments[?(@.environmentType=='DEV')].indexStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.environments[?(@.environmentType=='DEV')].documentCount").value(10));

    mockMvc
        .perform(
            post("/api/v1/deployment/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"환경 전환 테스트\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/deployment/environments"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.environments[?(@.environmentType=='DEV')].indexStatus").value("INACTIVE"))
        .andExpect(
            jsonPath("$.environments[?(@.environmentType=='PROD')].indexStatus").value("ACTIVE"))
        .andExpect(
            jsonPath("$.environments[?(@.environmentType=='PROD')].documentCount").value(10));
  }

  private void waitForAsyncTask(Long taskId, int maxWaitSeconds) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(maxWaitSeconds))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              AsyncTask task = asyncTaskRepository.findById(taskId).orElse(null);
              return task != null
                  && (task.getStatus() == AsyncTaskStatus.COMPLETED
                      || task.getStatus() == AsyncTaskStatus.FAILED);
            });
  }

  private Long executeIndexingRequest(String description) throws Exception {
    String requestBody = String.format("{\"description\":\"%s\"}", description);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/deployment/indexing")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString()).get("taskId").asLong();
  }

  private void verifyIndexingSuccess(Long taskId, Long expectedDocCount) {
    AsyncTask task = asyncTaskRepository.findById(taskId).orElseThrow();
    assertThat(task.getStatus()).isEqualTo(AsyncTaskStatus.COMPLETED);

    IndexEnvironment devEnv =
        environmentRepository.findByEnvironmentType(EnvironmentType.DEV).orElseThrow();
    assertThat(devEnv.getIndexStatus()).isEqualTo(IndexStatus.ACTIVE);
    assertThat(devEnv.getDocumentCount()).isEqualTo(expectedDocCount);
  }

  private void verifyDeploymentSuccess() {
    IndexEnvironment prodEnv =
        environmentRepository.findByEnvironmentType(EnvironmentType.PROD).orElseThrow();
    IndexEnvironment devEnv =
        environmentRepository.findByEnvironmentType(EnvironmentType.DEV).orElseThrow();

    assertThat(prodEnv.getIndexStatus()).isEqualTo(IndexStatus.ACTIVE);
    assertThat(prodEnv.getDocumentCount()).isEqualTo(10L);
    assertThat(devEnv.getIndexStatus()).isEqualTo(IndexStatus.INACTIVE);
  }

  private Integer sendIndexingRequest() {
    try {
      return mockMvc
          .perform(
              post("/api/v1/deployment/indexing")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"description\":\"동시성 테스트\"}"))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void createTestProducts() {
    List<Product> products = testDataLoader.loadProducts(10);
    productRepository.saveAll(products);
  }
}
