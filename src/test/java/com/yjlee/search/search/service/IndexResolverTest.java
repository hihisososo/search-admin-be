package com.yjlee.search.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yjlee.search.common.constants.ESFields;
import com.yjlee.search.config.IndexNameProvider;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.model.IndexEnvironment.EnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment.IndexStatus;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.search.exception.InvalidEnvironmentException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IndexResolver 테스트")
class IndexResolverTest {

  @Mock
  private IndexNameProvider indexNameProvider;

  @Mock
  private IndexEnvironmentRepository indexEnvironmentRepository;

  private IndexResolver indexResolver;

  @BeforeEach
  void setUp() {
    indexResolver = new IndexResolver(indexNameProvider, indexEnvironmentRepository);
  }

  @Test
  @DisplayName("기본 상품 인덱스 반환")
  void testResolveProductIndex() {
    // given
    when(indexNameProvider.getProductsSearchAlias()).thenReturn("products-search");

    // when
    String result = indexResolver.resolveProductIndex();

    // then
    assertThat(result).isEqualTo("products-search");
  }

  @Test
  @DisplayName("기본 자동완성 인덱스 반환")
  void testResolveAutocompleteIndex() {
    // when
    String result = indexResolver.resolveAutocompleteIndex();

    // then
    assertThat(result).isEqualTo(ESFields.AUTOCOMPLETE_SEARCH_ALIAS);
  }

  @Test
  @DisplayName("환경별 상품 인덱스 반환 - DEV")
  void testResolveProductIndexForDev() {
    // given
    IndexEnvironment devEnv = createEnvironment(EnvironmentType.DEV, "products-dev-v1", IndexStatus.ACTIVE);
    when(indexEnvironmentRepository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnv));

    // when
    String result = indexResolver.resolveProductIndex(EnvironmentType.DEV);

    // then
    assertThat(result).isEqualTo("products-dev-v1");
  }

  @Test
  @DisplayName("환경별 상품 인덱스 반환 - PROD")
  void testResolveProductIndexForProd() {
    // given
    IndexEnvironment prodEnv = createEnvironment(EnvironmentType.PROD, "products-prod-v2", IndexStatus.ACTIVE);
    when(indexEnvironmentRepository.findByEnvironmentType(EnvironmentType.PROD))
        .thenReturn(Optional.of(prodEnv));

    // when
    String result = indexResolver.resolveProductIndex(EnvironmentType.PROD);

    // then
    assertThat(result).isEqualTo("products-prod-v2");
  }

  @Test
  @DisplayName("환경별 자동완성 인덱스 반환")
  void testResolveAutocompleteIndexForEnvironment() {
    // given
    IndexEnvironment devEnv = createEnvironment(EnvironmentType.DEV, "products-dev-v1", IndexStatus.ACTIVE);
    when(indexEnvironmentRepository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnv));
    when(indexNameProvider.getProductsIndexPrefix()).thenReturn("products");
    when(indexNameProvider.getAutocompleteIndex()).thenReturn("autocomplete");

    // when
    String result = indexResolver.resolveAutocompleteIndex(EnvironmentType.DEV);

    // then
    assertThat(result).isEqualTo("autocomplete-dev-v1");
  }

  @Test
  @DisplayName("시뮬레이션용 상품 인덱스 반환 - INACTIVE 상태 허용")
  void testResolveProductIndexForSimulation() {
    // given
    IndexEnvironment devEnv = createEnvironment(EnvironmentType.DEV, "products-dev-v1", IndexStatus.INACTIVE);
    when(indexEnvironmentRepository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnv));

    // when
    String result = indexResolver.resolveProductIndexForSimulation(EnvironmentType.DEV);

    // then
    assertThat(result).isEqualTo("products-dev-v1");
  }

  @Test
  @DisplayName("시뮬레이션용 자동완성 인덱스 반환")
  void testResolveAutocompleteIndexForSimulation() {
    // given
    IndexEnvironment devEnv = createEnvironment(EnvironmentType.DEV, "products-dev-v1", IndexStatus.INACTIVE);
    when(indexEnvironmentRepository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnv));
    when(indexNameProvider.getProductsIndexPrefix()).thenReturn("products");
    when(indexNameProvider.getAutocompleteIndex()).thenReturn("autocomplete");

    // when
    String result = indexResolver.resolveAutocompleteIndexForSimulation(EnvironmentType.DEV);

    // then
    assertThat(result).isEqualTo("autocomplete-dev-v1");
  }

  @Test
  @DisplayName("환경을 찾을 수 없을 때 예외 발생")
  void testThrowExceptionWhenEnvironmentNotFound() {
    // given
    when(indexEnvironmentRepository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> indexResolver.resolveProductIndex(EnvironmentType.DEV))
        .isInstanceOf(InvalidEnvironmentException.class)
        .hasMessageContaining("개발 환경을 찾을 수 없습니다.");
  }

  @Test
  @DisplayName("인덱스 이름이 없을 때 예외 발생")
  void testThrowExceptionWhenIndexNameIsNull() {
    // given
    IndexEnvironment devEnv = createEnvironment(EnvironmentType.DEV, null, IndexStatus.ACTIVE);
    when(indexEnvironmentRepository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnv));

    // when & then
    assertThatThrownBy(() -> indexResolver.resolveProductIndex(EnvironmentType.DEV))
        .isInstanceOf(InvalidEnvironmentException.class)
        .hasMessageContaining("개발 환경의 인덱스가 설정되지 않았습니다.");
  }

  @Test
  @DisplayName("인덱스 이름이 빈 문자열일 때 예외 발생")
  void testThrowExceptionWhenIndexNameIsEmpty() {
    // given
    IndexEnvironment devEnv = createEnvironment(EnvironmentType.DEV, "", IndexStatus.ACTIVE);
    when(indexEnvironmentRepository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnv));

    // when & then
    assertThatThrownBy(() -> indexResolver.resolveProductIndex(EnvironmentType.DEV))
        .isInstanceOf(InvalidEnvironmentException.class)
        .hasMessageContaining("개발 환경의 인덱스가 설정되지 않았습니다.");
  }

  @Test
  @DisplayName("인덱스가 활성 상태가 아닐 때 예외 발생")
  void testThrowExceptionWhenIndexNotActive() {
    // given
    IndexEnvironment devEnv = createEnvironment(EnvironmentType.DEV, "products-dev-v1", IndexStatus.INACTIVE);
    when(indexEnvironmentRepository.findByEnvironmentType(EnvironmentType.DEV))
        .thenReturn(Optional.of(devEnv));

    // when & then
    assertThatThrownBy(() -> indexResolver.resolveProductIndex(EnvironmentType.DEV))
        .isInstanceOf(InvalidEnvironmentException.class)
        .hasMessageContaining("개발 환경의 인덱스가 활성 상태가 아닙니다.");
  }

  private IndexEnvironment createEnvironment(EnvironmentType type, String indexName, IndexStatus status) {
    IndexEnvironment env = new IndexEnvironment();
    env.setEnvironmentType(type);
    env.setIndexName(indexName);
    env.setIndexStatus(status);
    return env;
  }
}
