package com.yjlee.search.recommendation.scheduler;

import com.yjlee.search.recommendation.service.RelatedKeywordCalculationService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RelatedKeywordBatchScheduler {

  private final RelatedKeywordCalculationService calculationService;

  @Scheduled(cron = "0 0 3 * * ?") // 매일 새벽 3시
  public void calculateDailyRelatedKeywords() {
    log.info("일일 연관검색어 계산 배치 시작");

    LocalDateTime to = LocalDateTime.now();
    LocalDateTime from = to.minusDays(7);

    try {
      calculationService.calculateRelatedKeywords(from, to);
      log.info("일일 연관검색어 계산 배치 완료");
    } catch (Exception e) {
      log.error("일일 연관검색어 계산 배치 실패", e);
    }
  }
}
