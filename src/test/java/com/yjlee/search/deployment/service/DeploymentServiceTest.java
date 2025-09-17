package com.yjlee.search.deployment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yjlee.search.async.model.AsyncTaskType;
import com.yjlee.search.async.service.AsyncTaskService;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.domain.DeploymentContext;
import com.yjlee.search.deployment.dto.DeploymentOperationResponse;
import com.yjlee.search.deployment.dto.DeploymentRequest;
import com.yjlee.search.deployment.enums.DeploymentType;
import com.yjlee.search.deployment.enums.IndexStatus;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.dictionary.common.service.DictionaryDataDeploymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

  @Mock private IndexEnvironmentService environmentService;
  @Mock private DictionaryDataDeploymentService dictionaryDataDeploymentService;
  @Mock private ElasticsearchIndexAliasService elasticsearchIndexAliasService;
  @Mock private ElasticsearchIndexService elasticsearchIndexService;
  @Mock private DeploymentHistoryService historyService;
  @Mock private AsyncTaskService asyncTaskService;

  @InjectMocks private DeploymentService deploymentService;

  private IndexEnvironment devEnvironment;
  private IndexEnvironment prodEnvironment;
  private DeploymentRequest request;
  private DeploymentHistory history;

  @BeforeEach
  void setUp() {
    devEnvironment =
        IndexEnvironment.builder()
            .id(1L)
            .environmentType(EnvironmentType.DEV)
            .indexStatus(IndexStatus.ACTIVE)
            .indexName("products_v202401011200")
            .autocompleteIndexName("products_ac_v202401011200")
            .version("v202401011200")
            .documentCount(1000L)
            .build();

    prodEnvironment =
        IndexEnvironment.builder()
            .id(2L)
            .environmentType(EnvironmentType.PROD)
            .indexStatus(IndexStatus.INACTIVE)
            .build();

    request = new DeploymentRequest();
    request.setDescription("테스트 배포");

    history =
        DeploymentHistory.builder()
            .id(1L)
            .deploymentType(DeploymentType.DEPLOYMENT)
            .version("v202401011200")
            .description("테스트 배포")
            .build();
  }

  @Test
  @DisplayName("배포 성공")
  void executeDeploymentSuccess() {
    when(environmentService.getEnvironment(EnvironmentType.DEV)).thenReturn(devEnvironment);
    when(environmentService.getOrCreateEnvironment(EnvironmentType.PROD))
        .thenReturn(prodEnvironment);
    when(asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING)).thenReturn(false);
    when(historyService.createHistory(any(), any(), any())).thenReturn(history);

    DeploymentOperationResponse response = deploymentService.executeDeployment(request);

    assertThat(response).isNotNull();
    assertThat(response.getMessage()).isEqualTo("배포완료");

    verify(environmentService, times(2)).getEnvironment(EnvironmentType.DEV);
    verify(environmentService).getOrCreateEnvironment(EnvironmentType.PROD);
    verify(historyService).createHistory(DeploymentType.DEPLOYMENT, "v202401011200", "테스트 배포");
    verify(environmentService).switchToProd();
    verify(environmentService).resetEnvironment(EnvironmentType.DEV);
    verify(dictionaryDataDeploymentService).copyFromDevToProd();
    verify(dictionaryDataDeploymentService).deleteAllByEnvironment(EnvironmentType.DEV);
    verify(elasticsearchIndexAliasService).updateAliases(any(), any());
    verify(historyService).updateHistoryStatus(1L, true, 1000L);
  }

  @Test
  @DisplayName("DEV 환경 비활성 상태일 때 배포 실패")
  void executeDeploymentFailsWhenDevNotActive() {
    IndexEnvironment inactiveDevEnv =
        IndexEnvironment.builder()
            .id(1L)
            .environmentType(EnvironmentType.DEV)
            .indexStatus(IndexStatus.INACTIVE)
            .build();

    when(environmentService.getEnvironment(EnvironmentType.DEV)).thenReturn(inactiveDevEnv);
    when(environmentService.getOrCreateEnvironment(EnvironmentType.PROD))
        .thenReturn(prodEnvironment);

    assertThatThrownBy(() -> deploymentService.executeDeployment(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("활성화된 색인이 없습니다");

    verify(historyService, never()).createHistory(any(), any(), any());
    verify(elasticsearchIndexAliasService, never()).updateAliases(any(), any());
  }

  @Test
  @DisplayName("색인 진행 중일 때 배포 실패")
  void executeDeploymentFailsWhenIndexingRunning() {
    when(environmentService.getEnvironment(EnvironmentType.DEV)).thenReturn(devEnvironment);
    when(environmentService.getOrCreateEnvironment(EnvironmentType.PROD))
        .thenReturn(prodEnvironment);
    when(asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING)).thenReturn(true);

    assertThatThrownBy(() -> deploymentService.executeDeployment(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("색인이 진행 중입니다");

    verify(historyService, never()).createHistory(any(), any(), any());
    verify(elasticsearchIndexAliasService, never()).updateAliases(any(), any());
  }

  @Test
  @DisplayName("배포 컨텍스트 준비")
  void prepareDeploymentContext() {
    when(environmentService.getEnvironment(EnvironmentType.DEV)).thenReturn(devEnvironment);
    when(environmentService.getOrCreateEnvironment(EnvironmentType.PROD))
        .thenReturn(prodEnvironment);

    DeploymentContext context = deploymentService.prepareDeploymentContext();

    assertThat(context).isNotNull();
    assertThat(context.getDevEnvironmentId()).isEqualTo(1L);
    assertThat(context.getProdEnvironmentId()).isEqualTo(2L);
    assertThat(context.getDevIndexName()).isEqualTo("products_v202401011200");
    assertThat(context.getDevAutocompleteIndexName()).isEqualTo("products_ac_v202401011200");
    assertThat(context.getDevVersion()).isEqualTo("v202401011200");
    assertThat(context.getDevDocumentCount()).isEqualTo(1000L);
  }

  @Test
  @DisplayName("환경 전환 처리")
  void updateEnvironments() {
    DeploymentContext context =
        DeploymentContext.builder().devEnvironmentId(1L).prodEnvironmentId(2L).build();

    deploymentService.updateEnvironments(context);

    verify(dictionaryDataDeploymentService).copyFromDevToProd();
    verify(environmentService).switchToProd();
    verify(environmentService).resetEnvironment(EnvironmentType.DEV);
    verify(dictionaryDataDeploymentService).deleteAllByEnvironment(EnvironmentType.DEV);
  }

  @Test
  @DisplayName("배포 중 예외 발생 시 히스토리 실패 처리")
  void handleDeploymentException() {
    when(environmentService.getEnvironment(EnvironmentType.DEV)).thenReturn(devEnvironment);
    when(environmentService.getOrCreateEnvironment(EnvironmentType.PROD))
        .thenReturn(prodEnvironment);
    when(asyncTaskService.hasRunningTask(AsyncTaskType.INDEXING)).thenReturn(false);
    when(historyService.createHistory(any(), any(), any())).thenReturn(history);
    doThrow(new RuntimeException("동기화 실패"))
        .when(dictionaryDataDeploymentService)
        .copyFromDevToProd();

    assertThatThrownBy(() -> deploymentService.executeDeployment(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("배포 실패");

    verify(historyService).updateHistoryStatus(1L, false, null);
  }
}
