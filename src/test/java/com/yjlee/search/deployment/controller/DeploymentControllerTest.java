package com.yjlee.search.deployment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yjlee.search.common.config.BaseIntegrationTest;
import com.yjlee.search.common.config.TestAwsConfig;
import com.yjlee.search.deployment.config.TestDeploymentConfig;
import com.yjlee.search.deployment.dto.DeploymentRequest;
import com.yjlee.search.deployment.dto.IndexingRequest;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.deployment.service.ElasticsearchIndexService;
import com.yjlee.search.deployment.service.SsmCommandService;
import com.yjlee.search.deployment.service.TestSsmCommandService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import({TestDeploymentConfig.class, TestAwsConfig.class})
class DeploymentControllerTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private IndexEnvironmentRepository indexEnvironmentRepository;

  @Autowired private DeploymentHistoryRepository deploymentHistoryRepository;

  @Autowired private ElasticsearchClient elasticsearchClient;

  @Autowired private ElasticsearchIndexService elasticsearchIndexService;

  @Autowired private SsmCommandService ssmCommandService;

  private TestSsmCommandService getTestSsmCommandService() {
    return (TestSsmCommandService) ssmCommandService;
  }

  @BeforeEach
  void setUp() throws Exception {
    // 테스트 데이터 초기화
    deploymentHistoryRepository.deleteAll();
    indexEnvironmentRepository.deleteAll();

    // 테스트용 SSM 서비스 초기화
    getTestSsmCommandService().reset();

    // 테스트용 인덱스 생성
    createTestIndices();

    // 기본 환경 설정
    IndexEnvironment devEnv = new IndexEnvironment();
    devEnv.setEnvironmentType(IndexEnvironment.EnvironmentType.DEV);
    devEnv.setIndexName("products_search_dev_test");
    devEnv.setVersion("v1");
    devEnv.setDocumentCount(1000L);
    devEnv.setIndexStatus(IndexEnvironment.IndexStatus.ACTIVE);
    devEnv.setIsIndexing(false);
    devEnv.setIndexDate(LocalDateTime.now());
    indexEnvironmentRepository.save(devEnv);

    IndexEnvironment prodEnv = new IndexEnvironment();
    prodEnv.setEnvironmentType(IndexEnvironment.EnvironmentType.PROD);
    prodEnv.setIndexName("products_search_prod_test");
    prodEnv.setVersion("v0");
    prodEnv.setDocumentCount(500L);
    prodEnv.setIndexStatus(IndexEnvironment.IndexStatus.ACTIVE);
    prodEnv.setIsIndexing(false);
    prodEnv.setIndexDate(LocalDateTime.now());
    indexEnvironmentRepository.save(prodEnv);

    // 테스트 배포 이력
    DeploymentHistory history1 = new DeploymentHistory();
    history1.setVersion("v1");
    history1.setDeploymentType(DeploymentHistory.DeploymentType.INDEXING);
    history1.setStatus(DeploymentHistory.DeploymentStatus.SUCCESS);
    history1.setDescription("테스트 색인 1");
    history1.setDocumentCount(1000L);
    history1.setDeploymentTime(LocalDateTime.now().minusHours(1));
    deploymentHistoryRepository.save(history1);

    DeploymentHistory history2 = new DeploymentHistory();
    history2.setVersion("v0");
    history2.setDeploymentType(DeploymentHistory.DeploymentType.DEPLOYMENT);
    history2.setStatus(DeploymentHistory.DeploymentStatus.SUCCESS);
    history2.setDescription("테스트 배포 1");
    history2.setDocumentCount(500L);
    history2.setDeploymentTime(LocalDateTime.now().minusDays(1).plusMinutes(10));
    deploymentHistoryRepository.save(history2);
  }

  @AfterEach
  void tearDown() throws Exception {
    // 테스트 인덱스 삭제
    deleteTestIndices();

    // SSM 서비스 상태 초기화
    getTestSsmCommandService().reset();
  }

  private void createTestIndices() throws Exception {
    // 개발 환경용 테스트 인덱스 생성
    String devIndexName = "products_search_dev_test";
    if (!elasticsearchIndexService.indexExists(devIndexName)) {
      CreateIndexRequest request =
          CreateIndexRequest.of(
              b ->
                  b.index(devIndexName)
                      .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                      .mappings(
                          m ->
                              m.properties("name", p -> p.text(t -> t))
                                  .properties("price", p -> p.long_(l -> l))));
      elasticsearchClient.indices().create(request);
    }

    // 개발 환경 인덱스에 테스트 문서 추가 (간단히 10개만)
    for (int i = 1; i <= 10; i++) {
      final int docId = i;
      var indexRequest =
          co.elastic.clients.elasticsearch.core.IndexRequest.of(
              idx ->
                  idx.index(devIndexName)
                      .id(String.valueOf(docId))
                      .document(Map.of("name", "Test Product " + docId, "price", docId * 1000)));
      elasticsearchClient.index(indexRequest);
    }

    // 운영 환경용 테스트 인덱스 생성
    String prodIndexName = "products_search_prod_test";
    if (!elasticsearchIndexService.indexExists(prodIndexName)) {
      CreateIndexRequest request =
          CreateIndexRequest.of(
              b ->
                  b.index(prodIndexName)
                      .settings(s -> s.numberOfShards("1").numberOfReplicas("0"))
                      .mappings(
                          m ->
                              m.properties("name", p -> p.text(t -> t))
                                  .properties("price", p -> p.long_(l -> l))));
      elasticsearchClient.indices().create(request);
    }

    // 운영 환경 인덱스에 테스트 문서 추가 (간단히 5개만)
    for (int i = 1; i <= 5; i++) {
      final int docId = i;
      var indexRequest =
          co.elastic.clients.elasticsearch.core.IndexRequest.of(
              idx ->
                  idx.index(prodIndexName)
                      .id(String.valueOf(docId))
                      .document(Map.of("name", "Prod Product " + docId, "price", docId * 2000)));
      elasticsearchClient.index(indexRequest);
    }

    // 인덱스 리프레시
    elasticsearchClient.indices().refresh(r -> r.index(devIndexName, prodIndexName));

    // Alias 설정
    try {
      elasticsearchIndexService.updateProductsSearchAlias(prodIndexName);
    } catch (Exception e) {
      // 이미 존재하는 경우 무시
    }
  }

  private void deleteTestIndices() throws Exception {
    // 테스트 인덱스 삭제
    elasticsearchIndexService.deleteIndexIfExists("products_search_dev_test");
    elasticsearchIndexService.deleteIndexIfExists("products_search_prod_test");
  }

  @Test
  @DisplayName("GET /api/v1/deployments/environments - 환경 목록 조회")
  void getEnvironments() throws Exception {
    mockMvc
        .perform(get("/api/v1/deployment/environments"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.environments", hasSize(2)))
        .andExpect(jsonPath("$.environments[0].environmentType").value("DEV"))
        .andExpect(jsonPath("$.environments[0].indexName").value("products_search_dev_test"))
        .andExpect(jsonPath("$.environments[0].documentCount").value(10))
        .andExpect(jsonPath("$.environments[1].environmentType").value("PROD"))
        .andExpect(jsonPath("$.environments[1].indexName").value("products_search_prod_test"));
  }

  @Test
  @DisplayName("POST /api/v1/deployments/indexing - 색인 실행")
  void executeIndexing() throws Exception {
    IndexingRequest request = new IndexingRequest();
    request.setDescription("통합 테스트 색인");

    mockMvc
        .perform(
            post("/api/v1/deployment/indexing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("색인이 시작되었습니다."))
        .andExpect(jsonPath("$.version").exists())
        .andExpect(jsonPath("$.historyId").exists());
  }

  @Test
  @DisplayName("POST /api/v1/deployments/deploy - 배포 실행")
  void executeDeployment() throws Exception {
    DeploymentRequest request = new DeploymentRequest();
    request.setDescription("통합 테스트 배포");

    mockMvc
        .perform(
            post("/api/v1/deployment/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("배포가 완료되었습니다."))
        .andExpect(jsonPath("$.version").value("v1"))
        .andExpect(jsonPath("$.historyId").exists());
  }

  @Test
  @DisplayName("GET /api/v1/deployment/history - 배포 이력 조회")
  void getDeploymentHistories() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/deployment/history")
                .param("page", "0")
                .param("size", "10")
                .param("sort", "createdAt,desc"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deploymentHistories", hasSize(2)))
        .andExpect(jsonPath("$.deploymentHistories[0].version").value("v0"))
        .andExpect(jsonPath("$.deploymentHistories[0].deploymentType").value("DEPLOYMENT"))
        .andExpect(jsonPath("$.deploymentHistories[0].status").value("SUCCESS"))
        .andExpect(jsonPath("$.pagination.totalElements").value(2));
  }

  @Test
  @DisplayName("GET /api/v1/deployment/history - 상태별 필터링")
  void getDeploymentHistories_FilterByStatus() throws Exception {
    // 실패한 이력 추가
    DeploymentHistory failedHistory = new DeploymentHistory();
    failedHistory.setVersion("v2");
    failedHistory.setDeploymentType(DeploymentHistory.DeploymentType.INDEXING);
    failedHistory.setStatus(DeploymentHistory.DeploymentStatus.FAILED);
    failedHistory.setDescription("실패한 색인");
    failedHistory.setDeploymentTime(LocalDateTime.now().minusMinutes(30));
    deploymentHistoryRepository.save(failedHistory);

    mockMvc
        .perform(get("/api/v1/deployment/history").param("status", "FAILED"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deploymentHistories", hasSize(1)))
        .andExpect(jsonPath("$.deploymentHistories[0].status").value("FAILED"));
  }

  @Test
  @DisplayName("GET /api/v1/deployment/history - 타입별 필터링")
  void getDeploymentHistories_FilterByType() throws Exception {
    mockMvc
        .perform(get("/api/v1/deployment/history").param("deploymentType", "DEPLOYMENT"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deploymentHistories", hasSize(1)))
        .andExpect(jsonPath("$.deploymentHistories[0].deploymentType").value("DEPLOYMENT"));
  }

  @Test
  @DisplayName("POST /api/v1/deployments/indexing - 색인 중복 실행 방지")
  void executeIndexing_PreventDuplicate() throws Exception {
    // 색인 상태를 진행 중으로 변경
    IndexEnvironment devEnv =
        indexEnvironmentRepository
            .findByEnvironmentType(IndexEnvironment.EnvironmentType.DEV)
            .orElseThrow();
    devEnv.setIsIndexing(true);
    indexEnvironmentRepository.save(devEnv);

    IndexingRequest request = new IndexingRequest();
    request.setDescription("중복 색인 시도");

    mockMvc
        .perform(
            post("/api/v1/deployment/indexing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("현재 색인이 진행 중입니다."));
  }

  @Test
  @DisplayName("POST /api/v1/deployments/deploy - 색인 없을 때 배포 실패")
  void executeDeployment_NoActiveIndex() throws Exception {
    // 개발 환경 색인 상태를 비활성화
    IndexEnvironment devEnv =
        indexEnvironmentRepository
            .findByEnvironmentType(IndexEnvironment.EnvironmentType.DEV)
            .orElseThrow();
    devEnv.setIndexStatus(IndexEnvironment.IndexStatus.INACTIVE);
    indexEnvironmentRepository.save(devEnv);

    DeploymentRequest request = new DeploymentRequest();
    request.setDescription("색인 없이 배포 시도");

    mockMvc
        .perform(
            post("/api/v1/deployment/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("개발 환경에 활성화된 색인이 없습니다."));
  }

  @Test
  @DisplayName("POST /api/v1/deployments/deploy - 실제 Elasticsearch와 통합 배포 테스트")
  void executeDeployment_WithElasticsearch() throws Exception {
    // 배포 실행
    DeploymentRequest request = new DeploymentRequest();
    request.setDescription("통합 테스트 배포 - ES 연동");

    mockMvc
        .perform(
            post("/api/v1/deployment/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("배포가 완료되었습니다."));

    // Alias 변경 확인
    var aliasIndices = elasticsearchIndexService.getCurrentAliasIndices();
    assertThat(aliasIndices).contains("products_search_dev_test");

    // SSM 명령 실행은 색인 시에만 발생하므로 배포 시에는 실행되지 않음
    // 배포는 단순히 Alias 변경만 수행
  }

  @Test
  @DisplayName("POST /api/v1/deployments/indexing - SSM을 통한 색인 실행")
  void executeIndexing_WithSSM() throws Exception {
    IndexingRequest request = new IndexingRequest();
    request.setDescription("SSM 테스트용 색인");

    mockMvc
        .perform(
            post("/api/v1/deployment/indexing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("색인이 시작되었습니다."));

    // 색인 시에는 SSM 명령이 비동기로 실행될 수 있음
    // 실제 구현에서는 EC2DeploymentService가 SSM을 통해 색인을 실행
  }
}
