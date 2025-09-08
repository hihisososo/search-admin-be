package com.yjlee.search.loggen.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.yjlee.search.clicklog.model.ClickLogDocument;
import com.yjlee.search.loggen.dto.BulkLogGenerationRequest;
import com.yjlee.search.loggen.model.SearchQueryPool;
import com.yjlee.search.loggen.repository.SearchQueryPoolRepository;
import com.yjlee.search.search.dto.SearchExecuteRequest;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import com.yjlee.search.search.service.SearchService;
import com.yjlee.search.searchlog.model.SearchLogDocument;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkLogGeneratorService {

  private final ElasticsearchClient elasticsearchClient;
  private final SearchQueryPoolRepository queryPoolRepository;
  private final SearchService searchService;

  private static final String SEARCH_LOG_INDEX_PREFIX = "search-logs-";
  private static final String CLICK_LOG_INDEX_PREFIX = "click-logs-";
  private static final DateTimeFormatter INDEX_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy.MM.dd");

  private final Random random = new Random();

  public void generateBulkLogs(BulkLogGenerationRequest request) {
    LocalDate startDate = request.getStartDate();
    LocalDate endDate = request.getEndDate();
    int logsPerDay = request.getLogsPerDay();
    double clickRate = request.getClickRate();

    if (startDate.isAfter(endDate)) {
      throw new IllegalArgumentException("시작 날짜가 종료 날짜보다 늦을 수 없습니다.");
    }

    long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
    long totalLogs = totalDays * logsPerDay;

    log.info(
        "대량 로그 생성 시작 - 기간: {} ~ {}, 일별: {}개, 총: {}개", startDate, endDate, logsPerDay, totalLogs);

    // 쿼리 풀 로드
    List<SearchQueryPool> queryPool = queryPoolRepository.findAll();
    if (queryPool.isEmpty()) {
      throw new IllegalStateException("검색어 풀이 비어있습니다. 먼저 검색어를 생성해주세요.");
    }

    ExecutorService executor = Executors.newFixedThreadPool(10);
    AtomicInteger processedCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);

    try {
      List<CompletableFuture<Void>> futures = new ArrayList<>();

      // 날짜별로 처리
      LocalDate currentDate = startDate;
      while (!currentDate.isAfter(endDate)) {
        LocalDate processDate = currentDate;

        // 배치 처리 (100개씩)
        int batchSize = 100;
        for (int i = 0; i < logsPerDay; i += batchSize) {
          int batchStart = i;
          int batchEnd = Math.min(i + batchSize, logsPerDay);

          CompletableFuture<Void> future =
              CompletableFuture.runAsync(
                  () -> {
                    try {
                      processBatch(processDate, batchStart, batchEnd, queryPool, clickRate);
                      int processed = processedCount.addAndGet(batchEnd - batchStart);
                      if (processed % 1000 == 0) {
                        log.info("진행 상황: {}/{} 처리 완료", processed, totalLogs);
                      }
                    } catch (Exception e) {
                      log.error(
                          "배치 처리 실패 - 날짜: {}, 범위: {}-{}", processDate, batchStart, batchEnd, e);
                      errorCount.addAndGet(batchEnd - batchStart);
                    }
                  },
                  executor);

          futures.add(future);
        }

        currentDate = currentDate.plusDays(1);
      }

      // 모든 작업 완료 대기
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    } finally {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    log.info("대량 로그 생성 완료 - 성공: {}개, 실패: {}개", processedCount.get(), errorCount.get());
  }

  private void processBatch(
      LocalDate date, int startIdx, int endIdx, List<SearchQueryPool> queryPool, double clickRate) {
    List<SearchLogDocument> searchLogs = new ArrayList<>();
    List<ClickLogDocument> clickLogs = new ArrayList<>();

    for (int i = startIdx; i < endIdx; i++) {
      // 랜덤 시간 생성 (해당 날짜 내)
      LocalTime randomTime =
          LocalTime.of(random.nextInt(24), random.nextInt(60), random.nextInt(60));
      LocalDateTime timestamp = LocalDateTime.of(date, randomTime);

      // 랜덤 쿼리 선택
      SearchQueryPool randomQuery = queryPool.get(random.nextInt(queryPool.size()));
      String searchKeyword = randomQuery.getQuery();
      String sessionId = UUID.randomUUID().toString();

      // 검색 수행
      SearchExecuteRequest searchRequest = new SearchExecuteRequest();
      searchRequest.setQuery(searchKeyword);
      searchRequest.setSize(20);
      searchRequest.setSearchSessionId(sessionId);

      SearchExecuteResponse searchResponse = null;
      long responseTime = 0;

      try {
        long startTime = System.currentTimeMillis();
        searchResponse = searchService.searchProducts(searchRequest);
        responseTime = System.currentTimeMillis() - startTime;
      } catch (Exception e) {
        log.debug("검색 실행 실패 (무시하고 계속): {}", e.getMessage());
        responseTime = random.nextInt(100) + 50; // 50-150ms 랜덤
      }

      // 검색 로그 생성
      SearchLogDocument searchLog =
          SearchLogDocument.builder()
              .timestamp(timestamp)
              .searchKeyword(searchKeyword)
              .indexName("products")
              .responseTimeMs(responseTime)
              .resultCount(
                  searchResponse != null && searchResponse.getHits() != null
                      ? searchResponse.getHits().getTotal()
                      : 0L)
              .clientIp(generateRandomIp())
              .userAgent(generateRandomUserAgent())
              .isError(false)
              .sessionId(sessionId)
              .build();

      searchLogs.add(searchLog);

      // 클릭 로그 생성 (확률적으로)
      if (random.nextDouble() < clickRate) {
        if (searchResponse != null
            && searchResponse.getHits() != null
            && searchResponse.getHits().getData() != null
            && !searchResponse.getHits().getData().isEmpty()) {

          // 랜덤 상품 선택
          int productCount = searchResponse.getHits().getData().size();
          int clickPosition = selectPositionByProbability(productCount);
          String clickedProductId = searchResponse.getHits().getData().get(clickPosition).getId();

          // 클릭 시간은 검색 후 1-30초 사이
          LocalDateTime clickTime = timestamp.plusSeconds(random.nextInt(30) + 1);

          ClickLogDocument clickLog =
              ClickLogDocument.builder()
                  .timestamp(clickTime)
                  .searchKeyword(searchKeyword)
                  .clickedProductId(clickedProductId)
                  .indexName("products")
                  .sessionId(sessionId)
                  .build();

          clickLogs.add(clickLog);
        }
      }
    }

    // 벌크 저장
    saveBulkLogs(date, searchLogs, clickLogs);
  }

  private void saveBulkLogs(
      LocalDate date, List<SearchLogDocument> searchLogs, List<ClickLogDocument> clickLogs) {
    try {
      String searchIndexName = SEARCH_LOG_INDEX_PREFIX + date.format(INDEX_DATE_FORMAT);
      String clickIndexName = CLICK_LOG_INDEX_PREFIX + date.format(INDEX_DATE_FORMAT);

      BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

      // 검색 로그 추가
      for (SearchLogDocument searchLog : searchLogs) {
        bulkBuilder.operations(
            op -> op.index(idx -> idx.index(searchIndexName).document(searchLog)));
      }

      // 클릭 로그 추가
      for (ClickLogDocument clickLog : clickLogs) {
        bulkBuilder.operations(op -> op.index(idx -> idx.index(clickIndexName).document(clickLog)));
      }

      BulkRequest bulkRequest = bulkBuilder.build();
      BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest);

      if (bulkResponse.errors()) {
        int errorCnt = 0;
        for (BulkResponseItem item : bulkResponse.items()) {
          if (item.error() != null) {
            errorCnt++;
            log.debug("벌크 저장 실패 항목: {}", item.error().reason());
          }
        }
        log.warn("벌크 저장 부분 실패 - 성공: {}, 실패: {}", bulkResponse.items().size() - errorCnt, errorCnt);
      } else {
        log.debug("벌크 저장 성공 - 검색: {}개, 클릭: {}개", searchLogs.size(), clickLogs.size());
      }

    } catch (Exception e) {
      log.error("벌크 저장 실패", e);
      throw new RuntimeException("벌크 저장 실패", e);
    }
  }

  private int selectPositionByProbability(int resultSize) {
    double rand = random.nextDouble();
    // 1위: 40%, 2위: 25%, 3위: 15%, 4위: 10%, 5위 이하: 10%
    if (rand < 0.4) return 0;
    if (rand < 0.65 && resultSize > 1) return 1;
    if (rand < 0.8 && resultSize > 2) return 2;
    if (rand < 0.9 && resultSize > 3) return 3;
    // 5위 이하 랜덤
    if (resultSize > 4) {
      return Math.min(4 + random.nextInt(Math.min(5, resultSize - 4)), resultSize - 1);
    }
    return resultSize - 1;
  }

  private String generateRandomIp() {
    return String.format(
        "%d.%d.%d.%d",
        random.nextInt(256), random.nextInt(256), random.nextInt(256), random.nextInt(256));
  }

  private String generateRandomUserAgent() {
    String[] userAgents = {
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36",
      "Mozilla/5.0 (iPhone; CPU iPhone OS 14_7_1 like Mac OS X) AppleWebKit/605.1.15",
      "Mozilla/5.0 (Android 11; Mobile; rv:89.0) Gecko/89.0 Firefox/89.0"
    };
    return userAgents[random.nextInt(userAgents.length)];
  }
}
