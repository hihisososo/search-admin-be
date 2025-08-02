package com.yjlee.search.search.service;

import com.yjlee.search.config.IndexNameProvider;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import com.yjlee.search.search.exception.InvalidEnvironmentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IndexResolver {

  private final IndexNameProvider indexNameProvider;
  private final IndexEnvironmentRepository indexEnvironmentRepository;

  public String resolveProductIndex() {
    return indexNameProvider.getProductsSearchAlias();
  }

  public String resolveAutocompleteIndex() {
    return indexNameProvider.getAutocompleteIndex();
  }

  public String resolveProductIndex(IndexEnvironment.EnvironmentType environmentType) {
    IndexEnvironment environment = findAndValidateEnvironment(environmentType);
    return environment.getIndexName();
  }

  public String resolveAutocompleteIndex(IndexEnvironment.EnvironmentType environmentType) {
    IndexEnvironment environment = findAndValidateEnvironment(environmentType);
    return environment
        .getIndexName()
        .replace(
            indexNameProvider.getProductsIndexPrefix(), indexNameProvider.getAutocompleteIndex());
  }

  private IndexEnvironment findAndValidateEnvironment(
      IndexEnvironment.EnvironmentType environmentType) {
    IndexEnvironment environment =
        indexEnvironmentRepository
            .findByEnvironmentType(environmentType)
            .orElseThrow(
                () ->
                    new InvalidEnvironmentException(
                        environmentType.getDescription() + " 환경을 찾을 수 없습니다."));

    if (environment.getIndexName() == null || environment.getIndexName().isEmpty()) {
      throw new InvalidEnvironmentException(
          environmentType.getDescription() + " 환경의 인덱스가 설정되지 않았습니다.");
    }

    if (environment.getIndexStatus() != IndexEnvironment.IndexStatus.ACTIVE) {
      throw new InvalidEnvironmentException(
          environmentType.getDescription() + " 환경의 인덱스가 활성 상태가 아닙니다.");
    }

    return environment;
  }
}
