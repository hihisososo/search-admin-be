package com.yjlee.search.deployment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeploymentStepServiceTest {

  @Mock private IndexEnvironmentRepository indexEnvironmentRepository;
  @Mock private DeploymentHistoryRepository deploymentHistoryRepository;
  @Mock private ElasticsearchIndexService elasticsearchIndexService;

  @InjectMocks private DeploymentStepService deploymentStepService;

  private IndexEnvironment prodEnvironment;
  private IndexEnvironment devEnvironment;
  private DeploymentHistory deploymentHistory;

  @BeforeEach
  void setUp() {
    prodEnvironment = new IndexEnvironment();
    prodEnvironment.setEnvironmentType(IndexEnvironment.EnvironmentType.PROD);
    prodEnvironment.setIndexName("products_search_v1");

    devEnvironment = new IndexEnvironment();
    devEnvironment.setEnvironmentType(IndexEnvironment.EnvironmentType.DEV);
    devEnvironment.setIndexName("products_search_v2");
    devEnvironment.setVersion("v2");
    devEnvironment.setDocumentCount(1000L);

    deploymentHistory = new DeploymentHistory();
    deploymentHistory.setId(1L);
    deploymentHistory.setDeploymentType(DeploymentHistory.DeploymentType.DEPLOYMENT);
  }

  @Test
  @DisplayName("Alias 업데이트")
  void updateAlias() throws Exception {
    // Given
    when(elasticsearchIndexService.getCurrentAliasIndices())
        .thenReturn(java.util.Set.of("products_search_v1"));

    // When
    deploymentStepService.updateAlias(devEnvironment.getIndexName());

    // Then
    verify(elasticsearchIndexService, times(1))
        .updateProductsSearchAlias(devEnvironment.getIndexName());
    verify(elasticsearchIndexService, times(2)).getCurrentAliasIndices();
  }

  @Test
  @DisplayName("기존 운영 인덱스 삭제")
  void deleteOldProdIndex() throws Exception {
    // When
    deploymentStepService.deleteOldProdIndex(prodEnvironment);

    // Then
    verify(elasticsearchIndexService, times(1)).deleteIndexIfExists(prodEnvironment.getIndexName());
  }

  @Test
  @DisplayName("기존 운영 인덱스가 없을 때 삭제 스킵")
  void deleteOldProdIndex_NoIndex() throws Exception {
    // Given
    prodEnvironment.setIndexName(null);

    // When
    deploymentStepService.deleteOldProdIndex(prodEnvironment);

    // Then
    verify(elasticsearchIndexService, never()).deleteIndexIfExists(any());
  }

  @Test
  @DisplayName("운영 환경 정보 업데이트")
  void updateProdEnvironment() {
    // When
    deploymentStepService.updateProdEnvironment(prodEnvironment, devEnvironment);

    // Then
    verify(indexEnvironmentRepository, times(1)).save(prodEnvironment);
    assert prodEnvironment.getIndexName().equals(devEnvironment.getIndexName());
    assert prodEnvironment.getVersion().equals(devEnvironment.getVersion());
    assert prodEnvironment.getDocumentCount().equals(devEnvironment.getDocumentCount());
    assert prodEnvironment.getIndexStatus() == IndexEnvironment.IndexStatus.ACTIVE;
  }

  @Test
  @DisplayName("배포 이력 완료 처리")
  void completeDeploymentHistory() {
    // Given
    when(deploymentHistoryRepository.findById(1L)).thenReturn(Optional.of(deploymentHistory));

    // When
    deploymentStepService.completeDeploymentHistory(1L, 1000L);

    // Then
    verify(deploymentHistoryRepository, times(1)).findById(1L);
    verify(deploymentHistoryRepository, times(1)).save(deploymentHistory);
    assert deploymentHistory.getStatus() == DeploymentHistory.DeploymentStatus.SUCCESS;
  }

  @Test
  @DisplayName("배포 이력 실패 처리")
  void failDeploymentHistory() {
    // Given
    when(deploymentHistoryRepository.findById(1L)).thenReturn(Optional.of(deploymentHistory));

    // When
    deploymentStepService.failDeploymentHistory(1L);

    // Then
    verify(deploymentHistoryRepository, times(1)).findById(1L);
    verify(deploymentHistoryRepository, times(1)).save(deploymentHistory);
    assert deploymentHistory.getStatus() == DeploymentHistory.DeploymentStatus.FAILED;
  }

  @Test
  @DisplayName("인덱스 삭제 실패 시 경고 로그만 출력")
  void deleteOldProdIndex_FailureIgnored() throws Exception {
    // Given
    doThrow(new RuntimeException("삭제 실패"))
        .when(elasticsearchIndexService)
        .deleteIndexIfExists(any());

    // When
    deploymentStepService.deleteOldProdIndex(prodEnvironment);

    // Then
    verify(elasticsearchIndexService, times(1)).deleteIndexIfExists(prodEnvironment.getIndexName());
    // 예외가 발생해도 메서드는 정상 종료되어야 함
  }
}
