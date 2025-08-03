package com.yjlee.search.stats.service.query;

import com.yjlee.search.stats.domain.SearchStats;
import com.yjlee.search.stats.dto.StatsResponse;
import com.yjlee.search.stats.repository.StatsRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsQueryService {

  private final StatsRepository statsRepository;

  public StatsResponse getStats(LocalDateTime from, LocalDateTime to) {
    log.info("통계 조회 - 기간: {} ~ {}", from, to);
    
    SearchStats stats = statsRepository.getSearchStats(from, to);
    String period = from.toLocalDate() + " ~ " + to.toLocalDate();
    
    return StatsResponse.builder()
        .totalSearchCount(stats.getTotalSearchCount())
        .totalDocumentCount(stats.getTotalDocumentCount())
        .searchFailureRate(stats.getSearchFailureRate())
        .errorCount(stats.getErrorCount())
        .averageResponseTimeMs(stats.getAverageResponseTimeMs())
        .successRate(stats.getSuccessRate())
        .clickCount(stats.getClickCount())
        .clickThroughRate(stats.getClickThroughRate())
        .period(period)
        .build();
  }
}