package com.yjlee.search.deployment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.dto.EnvironmentListResponse;
import com.yjlee.search.deployment.enums.IndexStatus;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexEnvironmentServiceTest {

  @Mock private IndexEnvironmentRepository repository;
  @InjectMocks private IndexEnvironmentService environmentService;

  private IndexEnvironment devEnvironment;
  private IndexEnvironment prodEnvironment;

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
  }

  @Test
  @DisplayName("환경 조회 성공")
  void getEnvironmentSuccess() {
    when(repository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnvironment));

    IndexEnvironment result = environmentService.getEnvironment(EnvironmentType.DEV);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getEnvironmentType()).isEqualTo(EnvironmentType.DEV);
    assertThat(result.getIndexStatus()).isEqualTo(IndexStatus.ACTIVE);
    verify(repository).findByEnvironmentType(EnvironmentType.DEV);
  }

  @Test
  @DisplayName("환경 조회 실패 - 존재하지 않음")
  void getEnvironmentNotFound() {
    when(repository.findByEnvironmentType(EnvironmentType.DEV)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> environmentService.getEnvironment(EnvironmentType.DEV))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DEV 환경을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("환경 조회 - null 반환")
  void getEnvironmentOrNull() {
    when(repository.findByEnvironmentType(EnvironmentType.DEV)).thenReturn(Optional.empty());

    IndexEnvironment result = environmentService.getEnvironmentOrNull(EnvironmentType.DEV);

    assertThat(result).isNull();
    verify(repository).findByEnvironmentType(EnvironmentType.DEV);
  }

  @Test
  @DisplayName("환경 존재 여부 확인")
  void existsEnvironment() {
    when(repository.existsByEnvironmentType(EnvironmentType.DEV)).thenReturn(true);

    boolean exists = environmentService.existsEnvironment(EnvironmentType.DEV);

    assertThat(exists).isTrue();
    verify(repository).existsByEnvironmentType(EnvironmentType.DEV);
  }

  @Test
  @DisplayName("모든 환경 목록 조회")
  void getEnvironments() {
    List<IndexEnvironment> environments = Arrays.asList(devEnvironment, prodEnvironment);
    when(repository.findAll()).thenReturn(environments);

    EnvironmentListResponse response = environmentService.getEnvironments();

    assertThat(response).isNotNull();
    assertThat(response.getEnvironments()).hasSize(2);
    assertThat(response.getEnvironments().get(0).getEnvironmentType()).isEqualTo("DEV");
    assertThat(response.getEnvironments().get(1).getEnvironmentType()).isEqualTo("PROD");
    verify(repository).findAll();
  }

  @Test
  @DisplayName("환경 조회 또는 생성 - 기존 환경 반환")
  void getOrCreateEnvironmentReturnsExisting() {
    when(repository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnvironment));

    IndexEnvironment result = environmentService.getOrCreateEnvironment(EnvironmentType.DEV);

    assertThat(result).isEqualTo(devEnvironment);
    verify(repository).findByEnvironmentType(EnvironmentType.DEV);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("환경 조회 또는 생성 - 새 환경 생성")
  void getOrCreateEnvironmentCreatesNew() {
    IndexEnvironment newEnvironment = IndexEnvironment.createNew(EnvironmentType.DEV);
    when(repository.findByEnvironmentType(EnvironmentType.DEV)).thenReturn(Optional.empty());
    when(repository.save(any(IndexEnvironment.class))).thenReturn(newEnvironment);

    IndexEnvironment result = environmentService.getOrCreateEnvironment(EnvironmentType.DEV);

    assertThat(result).isNotNull();
    verify(repository).findByEnvironmentType(EnvironmentType.DEV);
    verify(repository).save(any(IndexEnvironment.class));
  }

  @Test
  @DisplayName("인덱스 활성화")
  void activateIndex() {
    when(repository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnvironment));
    when(repository.save(any(IndexEnvironment.class))).thenReturn(devEnvironment);

    environmentService.activateIndex(
        EnvironmentType.DEV,
        "product-search-v202401011300",
        "autocomplete-v202401011300",
        "synonyms-nori-dev",
        "v202401011300",
        2000L);

    verify(repository).findByEnvironmentType(EnvironmentType.DEV);
    verify(repository).save(devEnvironment);
    assertThat(devEnvironment.getVersion()).isEqualTo("v202401011300");
    assertThat(devEnvironment.getDocumentCount()).isEqualTo(2000L);
  }

  @Test
  @DisplayName("환경 초기화")
  void resetEnvironment() {
    when(repository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnvironment));
    when(repository.save(any(IndexEnvironment.class))).thenReturn(devEnvironment);

    environmentService.resetEnvironment(EnvironmentType.DEV);

    verify(repository).findByEnvironmentType(EnvironmentType.DEV);
    verify(repository).save(devEnvironment);
    assertThat(devEnvironment.getIndexStatus()).isEqualTo(IndexStatus.INACTIVE);
  }

  @Test
  @DisplayName("PROD 환경으로 전환")
  void switchToProd() {
    when(repository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnvironment));
    when(repository.findByEnvironmentType(EnvironmentType.PROD))
        .thenReturn(Optional.of(prodEnvironment));
    when(repository.save(any(IndexEnvironment.class))).thenReturn(prodEnvironment);

    environmentService.switchToProd();

    verify(repository).findByEnvironmentType(EnvironmentType.DEV);
    verify(repository).findByEnvironmentType(EnvironmentType.PROD);
    verify(repository).save(prodEnvironment);
    assertThat(prodEnvironment.getVersion()).isEqualTo("v202401011200");
    assertThat(prodEnvironment.getDocumentCount()).isEqualTo(1000L);
  }

  @Test
  @DisplayName("환경 생성")
  void createEnvironment() {
    IndexEnvironment newEnvironment = IndexEnvironment.createNew(EnvironmentType.DEV);
    when(repository.save(any(IndexEnvironment.class))).thenReturn(newEnvironment);

    IndexEnvironment result = environmentService.createEnvironment(EnvironmentType.DEV);

    assertThat(result).isNotNull();
    assertThat(result.getEnvironmentType()).isEqualTo(EnvironmentType.DEV);
    assertThat(result.getIndexStatus()).isEqualTo(IndexStatus.INACTIVE);
    verify(repository).save(any(IndexEnvironment.class));
  }
}
