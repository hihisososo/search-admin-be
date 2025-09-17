package com.yjlee.search.deployment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yjlee.search.deployment.dto.DeploymentHistoryListResponse;
import com.yjlee.search.deployment.enums.DeploymentStatus;
import com.yjlee.search.deployment.enums.DeploymentType;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DeploymentHistoryServiceTest {

  @Mock private DeploymentHistoryRepository historyRepository;
  @InjectMocks private DeploymentHistoryService historyService;

  private DeploymentHistory indexingHistory;
  private DeploymentHistory deploymentHistory;

  @BeforeEach
  void setUp() {
    indexingHistory =
        DeploymentHistory.builder()
            .id(1L)
            .deploymentType(DeploymentType.INDEXING)
            .version("v202401011200")
            .description("테스트 색인")
            .status(DeploymentStatus.IN_PROGRESS)
            .documentCount(1000L)
            .createdAt(LocalDateTime.now())
            .build();

    deploymentHistory =
        DeploymentHistory.builder()
            .id(2L)
            .deploymentType(DeploymentType.DEPLOYMENT)
            .version("v202401011200")
            .description("테스트 배포")
            .status(DeploymentStatus.SUCCESS)
            .documentCount(1000L)
            .createdAt(LocalDateTime.now())
            .deploymentTime(LocalDateTime.now())
            .build();
  }

  @Test
  @DisplayName("배포 이력 페이지 조회")
  void getDeploymentHistory() {
    Pageable pageable = PageRequest.of(0, 10);
    List<DeploymentHistory> histories = Arrays.asList(deploymentHistory, indexingHistory);
    Page<DeploymentHistory> page = new PageImpl<>(histories, pageable, 2);

    when(historyRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(page);

    DeploymentHistoryListResponse response = historyService.getDeploymentHistory(pageable);

    assertThat(response).isNotNull();
    assertThat(response.getDeploymentHistories()).hasSize(2);
    assertThat(response.getPagination().getTotalElements()).isEqualTo(2);
    verify(historyRepository).findAllByOrderByCreatedAtDesc(pageable);
  }

  @Test
  @DisplayName("색인 이력 생성")
  void createIndexingHistory() {
    DeploymentHistory newHistory =
        DeploymentHistory.createInProgress(DeploymentType.INDEXING, "v202401011300", "새 색인");
    when(historyRepository.save(any(DeploymentHistory.class))).thenReturn(newHistory);

    DeploymentHistory result =
        historyService.createHistory(DeploymentType.INDEXING, "v202401011300", "새 색인");

    assertThat(result).isNotNull();
    assertThat(result.getDeploymentType()).isEqualTo(DeploymentType.INDEXING);
    assertThat(result.getVersion()).isEqualTo("v202401011300");
    assertThat(result.getDescription()).isEqualTo("새 색인");
    assertThat(result.getStatus()).isEqualTo(DeploymentStatus.IN_PROGRESS);
    verify(historyRepository).save(any(DeploymentHistory.class));
  }

  @Test
  @DisplayName("배포 이력 생성")
  void createDeploymentHistory() {
    DeploymentHistory newHistory =
        DeploymentHistory.createInProgress(DeploymentType.DEPLOYMENT, "v202401011300", "새 배포");
    when(historyRepository.save(any(DeploymentHistory.class))).thenReturn(newHistory);

    DeploymentHistory result =
        historyService.createHistory(DeploymentType.DEPLOYMENT, "v202401011300", "새 배포");

    assertThat(result).isNotNull();
    assertThat(result.getDeploymentType()).isEqualTo(DeploymentType.DEPLOYMENT);
    assertThat(result.getVersion()).isEqualTo("v202401011300");
    assertThat(result.getDescription()).isEqualTo("새 배포");
    assertThat(result.getStatus()).isEqualTo(DeploymentStatus.IN_PROGRESS);
    verify(historyRepository).save(any(DeploymentHistory.class));
  }

  @Test
  @DisplayName("이력 상태 업데이트 - 성공")
  void updateHistoryStatusSuccess() {
    when(historyRepository.findById(1L)).thenReturn(Optional.of(indexingHistory));
    when(historyRepository.save(any(DeploymentHistory.class))).thenReturn(indexingHistory);

    historyService.updateHistoryStatus(1L, true, 2000L);

    ArgumentCaptor<DeploymentHistory> captor = ArgumentCaptor.forClass(DeploymentHistory.class);
    verify(historyRepository).save(captor.capture());

    DeploymentHistory savedHistory = captor.getValue();
    assertThat(savedHistory.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
    assertThat(savedHistory.getDocumentCount()).isEqualTo(2000L);
    assertThat(savedHistory.getDeploymentTime()).isNotNull();
  }

  @Test
  @DisplayName("이력 상태 업데이트 - 실패")
  void updateHistoryStatusFailure() {
    when(historyRepository.findById(1L)).thenReturn(Optional.of(indexingHistory));
    when(historyRepository.save(any(DeploymentHistory.class))).thenReturn(indexingHistory);

    historyService.updateHistoryStatus(1L, false, null);

    ArgumentCaptor<DeploymentHistory> captor = ArgumentCaptor.forClass(DeploymentHistory.class);
    verify(historyRepository).save(captor.capture());

    DeploymentHistory savedHistory = captor.getValue();
    assertThat(savedHistory.getStatus()).isEqualTo(DeploymentStatus.FAILED);
    assertThat(savedHistory.getDocumentCount()).isEqualTo(1000L);
  }

  @Test
  @DisplayName("이력 상태 업데이트 - 존재하지 않는 이력")
  void updateHistoryStatusNotFound() {
    when(historyRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> historyService.updateHistoryStatus(999L, true, 1000L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("이력을 찾을 수 없습니다: 999");

    verify(historyRepository, never()).save(any());
  }
}
