package com.yjlee.search.deployment.service;

import com.yjlee.search.deployment.dto.DeploymentHistoryListResponse;
import com.yjlee.search.deployment.dto.DeploymentHistoryResponse;
import com.yjlee.search.deployment.dto.EnvironmentInfoResponse;
import com.yjlee.search.deployment.dto.EnvironmentListResponse;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import com.yjlee.search.deployment.repository.IndexEnvironmentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnvironmentQueryService {

  private final IndexEnvironmentRepository environmentRepository;
  private final DeploymentHistoryRepository historyRepository;

  public EnvironmentListResponse getEnvironments() {
    List<IndexEnvironment> environments = environmentRepository.findAll();
    List<EnvironmentInfoResponse> responses =
        environments.stream().map(this::toEnvironmentResponse).toList();
    return EnvironmentListResponse.from(responses);
  }

  public DeploymentHistoryListResponse getDeploymentHistory(Pageable pageable) {
    Page<DeploymentHistory> histories = historyRepository.findAllByOrderByCreatedAtDesc(pageable);
    Page<DeploymentHistoryResponse> responses = histories.map(DeploymentHistoryResponse::from);
    return DeploymentHistoryListResponse.from(responses);
  }

  private EnvironmentInfoResponse toEnvironmentResponse(IndexEnvironment env) {
    return EnvironmentInfoResponse.builder()
        .environmentType(env.getEnvironmentType().name())
        .environmentDescription(env.getEnvironmentType().getDescription())
        .indexName(env.getIndexName())
        .autocompleteIndexName(env.getAutocompleteIndexName())
        .documentCount(env.getDocumentCount())
        .indexStatus(env.getIndexStatus().name())
        .indexStatusDescription(env.getIndexStatus().getDescription())
        .indexDate(env.getIndexDate())
        .version(env.getVersion())
        .build();
  }
}
