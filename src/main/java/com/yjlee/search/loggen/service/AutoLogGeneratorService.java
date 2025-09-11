package com.yjlee.search.loggen.service;

import com.yjlee.search.clicklog.dto.ClickLogRequest;
import com.yjlee.search.loggen.model.SearchQueryPool;
import com.yjlee.search.loggen.repository.SearchQueryPoolRepository;
import com.yjlee.search.search.dto.ProductDto;
import com.yjlee.search.search.dto.SearchExecuteResponse;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoLogGeneratorService {

  private final SearchQueryPoolRepository queryPoolRepository;
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${app.log-generator.enabled:false}")
  private boolean enabledByConfig;

  @Value("${app.log-generator.parallel-llm-count:3}")
  private int parallelLlmCount = 3;

  @Value("${server.port:8080}")
  private int serverPort;

  private final Random random = new Random();
  private ScheduledExecutorService scheduledExecutor;
  private volatile boolean running = false;
  private volatile boolean manualOverride = false;

  public void startGeneration() {
    log.info("수동으로 로그 생성 시작 요청");
    manualOverride = true;
    System.setProperty("app.log-generator.enabled", "true");
  }

  public void stopGeneration() {
    log.info("수동으로 로그 생성 중지 요청");
    manualOverride = false;
    System.setProperty("app.log-generator.enabled", "false");
  }

  public boolean isRunning() {
    return running;
  }

  @Scheduled(fixedDelay = 5000) // 5초마다 상태 확인
  public void checkAndStartWorkers() {
    boolean enabled =
        Boolean.parseBoolean(
            System.getProperty("app.log-generator.enabled", String.valueOf(enabledByConfig)));

    if (enabled && !running) {
      // 시작
      running = true;
      log.info("LLM 로그 생성 워커 {}EA 시작", parallelLlmCount);
      
      // ScheduledExecutorService 생성
      scheduledExecutor = Executors.newScheduledThreadPool(parallelLlmCount);
      
      for (int i = 0; i < parallelLlmCount; i++) {
        final int workerId = i + 1;
        // 1초마다 실행되는 스케줄 태스크로 변경
        scheduledExecutor.scheduleWithFixedDelay(
            () -> runWorkerTask(workerId),
            0,
            1,
            TimeUnit.SECONDS
        );
      }
    } else if (!enabled && running) {
      // 종료
      running = false;
      log.info("LLM 로그 생성 워커 중지 요청");
      if (scheduledExecutor != null) {
        scheduledExecutor.shutdown();
      }
    }
  }

  private void runWorkerTask(int workerId) {
    if (!running) {
      return;
    }
    
    try {
      // 로그 생성
      generateLLMBasedLog();
    } catch (Exception e) {
      log.error("LLM 워커 {} 에러: {}", workerId, e.getMessage());
    }
  }

  private void generateLLMBasedLog() {
    try {
      // 1. DB에서 랜덤 검색어 가져오기
      Optional<SearchQueryPool> poolQuery = queryPoolRepository.findRandomQuery();
      if (poolQuery.isEmpty()) {
        log.warn("검색어 풀이 비어있음");
        return;
      }

      String searchQuery = poolQuery.get().getQuery();
      log.debug("검색어 풀에서 선택: {}", searchQuery);

      // 2. 실제 검색 수행 (HTTP 요청으로)
      String sessionId = UUID.randomUUID().toString();
      String baseUrl = "http://localhost:" + serverPort;

      // URL 인코딩을 위해 UriComponentsBuilder 사용
      String searchUrl =
          UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/v1/search")
              .queryParam("query", searchQuery)
              .queryParam("size", 20)
              .queryParam("searchSessionId", sessionId)
              .build()
              .toUriString();

      ResponseEntity<SearchExecuteResponse> responseEntity =
          restTemplate.getForEntity(searchUrl, SearchExecuteResponse.class);
      SearchExecuteResponse searchResponse = responseEntity.getBody();

      if (searchResponse == null
          || searchResponse.getHits() == null
          || searchResponse.getHits().getData() == null
          || searchResponse.getHits().getData().isEmpty()) {
        log.info("검색 결과 없음: {}", searchQuery);
        return;
      }

      // 3. 확률적 클릭 결정 (70% 확률로 클릭)
      if (shouldClick()) {
        List<ProductDto> products = searchResponse.getHits().getData();
        int position = selectPositionByProbability(products.size());
        ProductDto clickedProduct = products.get(position);

        generateClickLog(searchQuery, clickedProduct, sessionId);
        log.info(
            "클릭 로그 생성 - 키워드: {}, 클릭 상품: {}, 순위: {}",
            searchQuery,
            clickedProduct.getName(),
            position + 1);
      } else {
        log.info("검색만 수행 (클릭 없음) - 키워드: {}", searchQuery);
      }

    } catch (Exception e) {
      log.error("로그 생성 실패", e);
    }
  }

  private boolean shouldClick() {
    // 70% 확률로 클릭
    return random.nextDouble() < 0.7;
  }

  private int selectPositionByProbability(int resultSize) {
    double rand = random.nextDouble();
    // 1위: 40%, 2위: 25%, 3위: 15%, 4위: 10%, 5위 이하: 10%
    if (rand < 0.4) return 0;
    if (rand < 0.65 && resultSize > 1) return 1;
    if (rand < 0.8 && resultSize > 2) return 2;
    if (rand < 0.9 && resultSize > 3) return 3;
    // 5위 이하 랜덤
    return Math.min(4 + random.nextInt(Math.min(5, resultSize - 4)), resultSize - 1);
  }

  private void generateClickLog(String searchKeyword, ProductDto clickedProduct, String sessionId) {
    try {
      ClickLogRequest clickRequest =
          ClickLogRequest.builder()
              .searchKeyword(searchKeyword)
              .clickedProductId(clickedProduct.getId())
              .indexName("products-search")
              .sessionId(sessionId)
              .build();

      // HTTP 요청으로 클릭 로그 전송
      String baseUrl = "http://localhost:" + serverPort;
      String clickUrl = baseUrl + "/api/v1/click-logs";

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<ClickLogRequest> entity = new HttpEntity<>(clickRequest, headers);

      restTemplate.postForEntity(clickUrl, entity, Object.class);

    } catch (Exception e) {
      log.error("클릭 로그 생성 실패: {}", searchKeyword, e);
    }
  }

  @PreDestroy
  public void shutdown() {
    log.info("AutoLogGeneratorService 종료 시작");
    running = false;
    if (scheduledExecutor != null) {
      scheduledExecutor.shutdown();
      try {
        if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          scheduledExecutor.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduledExecutor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }
}
