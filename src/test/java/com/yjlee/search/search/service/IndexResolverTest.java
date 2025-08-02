package com.yjlee.search.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yjlee.search.config.IndexNameProvider;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.search.exception.InvalidEnvironmentException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexResolverTest {

  @Mock private IndexNameProvider indexNameProvider;
  @Mock private IndexEnvironmentRepository indexEnvironmentRepository;

  @InjectMocks private IndexResolver indexResolver;

  @Test
  @DisplayName("기본 상품 인덱스명 반환")
  void testResolveProductIndex() {
    when(indexNameProvider.getProductsSearchAlias()).thenReturn("products");

    String result = indexResolver.resolveProductIndex();

    assertThat(result).isEqualTo("products");
  }

  @Test
  @DisplayName("기본 자동완성 인덱스명 반환")
  void testResolveAutocompleteIndex() {
    when(indexNameProvider.getAutocompleteIndex()).thenReturn("autocomplete");

    String result = indexResolver.resolveAutocompleteIndex();

    assertThat(result).isEqualTo("autocomplete");
  }

  @Test
  @DisplayName("환경별 상품 인덱스명 반환")
  void testResolveProductIndexWithEnvironment() {
    IndexEnvironment environment = new IndexEnvironment();
    environment.setIndexName("products-dev");
    environment.setIndexStatus(IndexEnvironment.IndexStatus.ACTIVE);

    when(indexEnvironmentRepository.findByEnvironmentType(IndexEnvironment.EnvironmentType.DEV))
        .thenReturn(Optional.of(environment));

    String result = indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV);

    assertThat(result).isEqualTo("products-dev");
  }

  @Test
  @DisplayName("환경이 없을 때 예외 발생")
  void testResolveProductIndexWithInvalidEnvironment() {
    when(indexEnvironmentRepository.findByEnvironmentType(any())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> indexResolver.resolveProductIndex(IndexEnvironment.EnvironmentType.DEV))
        .isInstanceOf(InvalidEnvironmentException.class)
        .hasMessageContaining("환경을 찾을 수 없습니다");
  }
}
