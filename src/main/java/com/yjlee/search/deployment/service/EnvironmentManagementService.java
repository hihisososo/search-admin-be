package com.yjlee.search.deployment.service;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.domain.IndexingContext;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexEnvironmentService {

  private final IndexEnvironmentRepository repository;

  @Transactional(readOnly = true)
  public IndexEnvironment getEnvironment(EnvironmentType type) {
    return repository
        .findByEnvironmentType(type)
        .orElseThrow(() -> new IllegalStateException(type + " 환경을 찾을 수 없습니다"));
  }

  @Transactional(readOnly = true)
  public IndexEnvironment getEnvironmentOrNull(EnvironmentType type) {
    return repository.findByEnvironmentType(type).orElse(null);
  }

  @Transactional
  public IndexEnvironment getOrCreateEnvironment(EnvironmentType type) {
    return repository.findByEnvironmentType(type).orElseGet(() -> createEnvironment(type));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IndexEnvironment createEnvironment(EnvironmentType type) {
    IndexEnvironment environment =
        IndexEnvironment.builder()
            .environmentType(type)
            .indexStatus(IndexEnvironment.IndexStatus.INACTIVE)
            .documentCount(0L)
            .build();
    return repository.save(environment);
  }

  @Transactional
  public void prepareForIndexing(EnvironmentType type, IndexingContext context) {
    IndexEnvironment env =
        repository
            .findByEnvironmentType(type)
            .orElseThrow(() -> new IllegalStateException(type + " 환경을 찾을 수 없습니다"));

    log.info(
        "{} 환경 색인 준비 - 인덱스: {}, 버전: {}", type, context.getProductIndexName(), context.getVersion());

    env.reset();
    env.updatePrepareIndexing(
        context.getProductIndexName(),
        context.getAutocompleteIndexName(),
        context.getSynonymSetName(),
        context.getVersion());

    repository.save(env);
    log.info("{} 환경 색인 준비 완료", type);
  }

  @Transactional
  public void activateIndex(EnvironmentType type, String version, Long documentCount) {
    IndexEnvironment env =
        repository
            .findByEnvironmentType(type)
            .orElseThrow(() -> new IllegalStateException(type + " 환경을 찾을 수 없습니다"));

    log.info("{} 환경 활성화 - 버전: {}, 문서수: {}", type, version, documentCount);

    env.activate(version, documentCount);
    repository.save(env);

    log.info("{} 환경 활성화 완료", type);
  }

  @Transactional
  public void resetEnvironment(EnvironmentType type) {
    IndexEnvironment env =
        repository
            .findByEnvironmentType(type)
            .orElseThrow(() -> new IllegalStateException(type + " 환경을 찾을 수 없습니다"));

    log.info("{} 환경 초기화", type);
    env.reset();
    repository.save(env);
    log.info("{} 환경 초기화 완료", type);
  }

  @Transactional
  public void switchToProd() {
    IndexEnvironment devEnv =
        repository
            .findByEnvironmentType(EnvironmentType.DEV)
            .orElseThrow(() -> new IllegalStateException("DEV 환경을 찾을 수 없습니다"));

    IndexEnvironment prodEnv =
        repository
            .findByEnvironmentType(EnvironmentType.PROD)
            .orElseThrow(() -> new IllegalStateException("PROD 환경을 찾을 수 없습니다"));

    log.info("환경 전환 시작: DEV → PROD", devEnv.getIndexName());

    prodEnv.switchFrom(devEnv);
    repository.save(prodEnv);

    log.info("PROD 환경 업데이트 완료: {}", prodEnv.getIndexName());
  }

  @Transactional(readOnly = true)
  public boolean existsEnvironment(EnvironmentType type) {
    return repository.existsByEnvironmentType(type);
  }

}
