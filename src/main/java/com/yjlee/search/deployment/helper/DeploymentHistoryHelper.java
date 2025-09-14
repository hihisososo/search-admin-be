package com.yjlee.search.deployment.helper;

import com.yjlee.search.deployment.model.DeploymentHistory;
import com.yjlee.search.deployment.repository.DeploymentHistoryRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeploymentHistoryHelper {

  private final DeploymentHistoryRepository historyRepository;

  /** 새로운 버전 생성 */
  public String generateVersion() {
    return "v" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }

  /** 배포 이력 생성 */
  @Transactional
  public DeploymentHistory createHistory(
      DeploymentHistory.DeploymentType type, String version, String description) {
    DeploymentHistory history =
        DeploymentHistory.builder()
            .deploymentType(type)
            .status(DeploymentHistory.DeploymentStatus.IN_PROGRESS)
            .version(version)
            .description(description != null ? description : type.name())
            .build();
    return historyRepository.save(history);
  }

  /** 이력 상태 업데이트 */
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
