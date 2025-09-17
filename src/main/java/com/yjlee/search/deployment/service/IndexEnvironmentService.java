package com.yjlee.search.deployment.service;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.dto.EnvironmentInfoResponse;
import com.yjlee.search.deployment.dto.EnvironmentListResponse;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class IndexEnvironmentService {

  private final IndexEnvironmentRepository repository;

  @Transactional(readOnly = true)
  public IndexEnvironment getEnvironment(EnvironmentType type) {
    return findByTypeOrThrow(type);
  }

  @Transactional(readOnly = true)
  public IndexEnvironment getEnvironmentOrNull(EnvironmentType type) {
    return repository.findByEnvironmentType(type).orElse(null);
  }

  @Transactional(readOnly = true)
  public boolean existsEnvironment(EnvironmentType type) {
    return repository.existsByEnvironmentType(type);
  }

  @Transactional(readOnly = true)
  public EnvironmentListResponse getEnvironments() {
    List<IndexEnvironment> environments = repository.findAll();
    List<EnvironmentInfoResponse> responses =
        environments.stream().map(EnvironmentInfoResponse::from).toList();
    return EnvironmentListResponse.from(responses);
  }

  public IndexEnvironment getOrCreateEnvironment(EnvironmentType type) {
    return repository.findByEnvironmentType(type).orElseGet(() -> createEnvironment(type));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IndexEnvironment createEnvironment(EnvironmentType type) {
    IndexEnvironment environment = IndexEnvironment.createNew(type);
    return repository.save(environment);
  }

  public void activateIndex(
      EnvironmentType type,
      String indexName,
      String autocompleteIndexName,
      String synonymSetName,
      String version,
      Long documentCount) {
    IndexEnvironment env = findByTypeOrThrow(type);
    env.activate(indexName, autocompleteIndexName, synonymSetName, version, documentCount);
    repository.save(env);
  }

  public void resetEnvironment(EnvironmentType type) {
    IndexEnvironment env = findByTypeOrThrow(type);
    env.reset();
    repository.save(env);
  }

  public void updatePrepareIndexing(
      EnvironmentType type,
      String indexName,
      String autocompleteIndexName,
      String synonymSetName,
      String version) {
    IndexEnvironment env = findByTypeOrThrow(type);
    env.updatePrepareIndexing(indexName, autocompleteIndexName, synonymSetName, version);
    repository.save(env);
  }

  public void switchToProd() {
    IndexEnvironment devEnv = findByTypeOrThrow(EnvironmentType.DEV);
    IndexEnvironment prodEnv = findByTypeOrThrow(EnvironmentType.PROD);
    prodEnv.switchFrom(devEnv);
    repository.save(prodEnv);
  }

  private IndexEnvironment findByTypeOrThrow(EnvironmentType type) {
    return repository
        .findByEnvironmentType(type)
        .orElseThrow(() -> new IllegalStateException(type + " 환경을 찾을 수 없습니다"));
  }
}
