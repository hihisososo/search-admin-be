package com.yjlee.search.deployment.service;

import com.yjlee.search.deployment.dto.DeploymentHistoryListResponse;
import com.yjlee.search.deployment.dto.DeploymentHistoryResponse;
import com.yjlee.search.deployment.enums.DeploymentType;
import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeploymentHistoryService {

  private final DeploymentHistoryRepository historyRepository;

  @Transactional(readOnly = true)
  public DeploymentHistoryListResponse getDeploymentHistory(Pageable pageable) {
    Page<DeploymentHistory> histories = historyRepository.findAllByOrderByCreatedAtDesc(pageable);
    Page<DeploymentHistoryResponse> responses = histories.map(DeploymentHistoryResponse::from);
    return DeploymentHistoryListResponse.from(responses);
  }

  public DeploymentHistory createHistory(DeploymentType type, String version, String description) {
    DeploymentHistory history = DeploymentHistory.createInProgress(type, version, description);
    return historyRepository.save(history);
  }

  public void updateHistoryStatus(Long historyId, boolean success, Long documentCount) {
    DeploymentHistory history =
        historyRepository
            .findById(historyId)
            .orElseThrow(() -> new IllegalStateException("이력을 찾을 수 없습니다: " + historyId));

    if (success) {
      history.complete(LocalDateTime.now(), documentCount);
    } else {
      history.fail();
    }
    historyRepository.save(history);
  }
}
