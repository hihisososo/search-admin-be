package com.yjlee.search.deployment.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yjlee.search.deployment.model.IndexEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DevEnvironmentStrategyTest {

  private DevEnvironmentStrategy strategy;
  private IndexEnvironment devEnvironment;
  private IndexEnvironment prodEnvironment;

  @BeforeEach
  void setUp() {
    strategy = new DevEnvironmentStrategy();

    devEnvironment = new IndexEnvironment();
    devEnvironment.setEnvironmentType(IndexEnvironment.EnvironmentType.DEV);
    devEnvironment.setIsIndexing(false);
    devEnvironment.setIndexStatus(IndexEnvironment.IndexStatus.ACTIVE);

    prodEnvironment = new IndexEnvironment();
    prodEnvironment.setEnvironmentType(IndexEnvironment.EnvironmentType.PROD);
  }

  @Test
  @DisplayName("색인 중이 아닐 때 배포 가능")
  void canDeploy_NotIndexing() {
    // Given
    devEnvironment.setIsIndexing(false);

    // When
    boolean result = strategy.canDeploy(devEnvironment);

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("색인 중일 때 배포 불가")
  void canDeploy_Indexing() {
    // Given
    devEnvironment.setIsIndexing(true);

    // When
    boolean result = strategy.canDeploy(devEnvironment);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("활성화된 색인이 있고 색인 중이 아닐 때 검증 통과")
  void validateDeployment_Success() {
    // Given
    devEnvironment.setIndexStatus(IndexEnvironment.IndexStatus.ACTIVE);
    devEnvironment.setIsIndexing(false);

    // When & Then - 예외가 발생하지 않음
    strategy.validateDeployment(devEnvironment, prodEnvironment);
  }

  @Test
  @DisplayName("활성화된 색인이 없을 때 검증 실패")
  void validateDeployment_NoActiveIndex() {
    // Given
    devEnvironment.setIndexStatus(IndexEnvironment.IndexStatus.INACTIVE);
    devEnvironment.setIsIndexing(false);

    // When & Then
    assertThatThrownBy(() -> strategy.validateDeployment(devEnvironment, prodEnvironment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("개발 환경에 활성화된 색인이 없습니다.");
  }

  @Test
  @DisplayName("색인 중일 때 검증 실패")
  void validateDeployment_Indexing() {
    // Given
    devEnvironment.setIndexStatus(IndexEnvironment.IndexStatus.ACTIVE);
    devEnvironment.setIsIndexing(true);

    // When & Then
    assertThatThrownBy(() -> strategy.validateDeployment(devEnvironment, prodEnvironment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("개발 환경에서 색인이 진행 중입니다.");
  }

  @Test
  @DisplayName("배포 준비 - 특별한 작업 없음")
  void prepareDeployment() {
    // When & Then - 예외가 발생하지 않음
    strategy.prepareDeployment(devEnvironment);
  }

  @Test
  @DisplayName("배포 후처리 - 특별한 작업 없음")
  void postDeployment() {
    // When & Then - 예외가 발생하지 않음
    strategy.postDeployment(devEnvironment);
  }

  @Test
  @DisplayName("환경 이름 반환")
  void getEnvironmentName() {
    // When
    String name = strategy.getEnvironmentName();

    // Then
    assertThat(name).isEqualTo("DEV");
  }
}
