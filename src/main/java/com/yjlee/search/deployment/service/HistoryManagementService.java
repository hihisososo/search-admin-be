package com.yjlee.search.deployment.service;

import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentHistoryService {

  private final DeploymentHistoryRepository historyRepository;

  @Transactional
  public DeploymentHistory createHistory(
      DeploymentHistory.DeploymentType type, String version, String description) {
    DeploymentHistory history =
        DeploymentHistory.builder()
            .deploymentType(type)
            .status(DeploymentHistory.DeploymentStatus.IN_PROGRESS)
            .version(version)
            .description(description)
            .build();
    return historyRepository.save(history);
  }

  @Transactional
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
