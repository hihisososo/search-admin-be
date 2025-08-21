package com.yjlee.search.evaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

  private final ObjectMapper objectMapper;

  @Value("${openai.api.url}")
  private String openaiApiUrl;

  @Value("${openai.api.key}")
  private String openaiApiKey;

  @Value("${openai.api.model:gpt-5-nano}")
  private String openaiModel;

  @Value("${openai.api.connect-timeout-ms:8000}")
  private int connectTimeoutMs;

  @Value("${openai.api.read-timeout-ms:45000}")
  private int readTimeoutMs;

  @Value("${openai.api.max-retries:3}")
  private int maxRetries;

  @Value("${openai.api.initial-backoff-ms:1000}")
  private int initialBackoffMs;

  public String callLLMAPI(String prompt) {
    try {
      log.debug("LLM API 호출 시작");
      return performAPICall(prompt, null);
    } catch (Exception e) {
      log.error("LLM API 호출 실패: {}", e.getMessage(), e);
      throw new RuntimeException("LLM API 호출 실패: " + e.getMessage(), e);
    }
  }

  public String callLLMAPI(String prompt, Double temperature) {
    try {
      // temperature 파라미터는 더 이상 사용하지 않음 (모델 정책에 따라 미지원)
      log.debug("LLM API 호출 시작 (temperature 파라미터 무시)");
      return performAPICall(prompt, null);
    } catch (Exception e) {
      log.error("LLM API 호출 실패: {}", e.getMessage(), e);
      throw new RuntimeException("LLM API 호출 실패: " + e.getMessage(), e);
    }
  }

  private String performAPICall(String prompt, Double temperature) throws Exception {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    RestTemplate timedRestTemplate = new RestTemplate(requestFactory);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "Bearer " + openaiApiKey);

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("model", openaiModel);
    requestBody.put("messages", Arrays.asList(Map.of("role", "user", "content", prompt)));

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

    int attempt = 0;
    RuntimeException lastEx = null;
    while (attempt < Math.max(1, maxRetries)) {
      attempt++;
      try {
        ResponseEntity<String> response =
            timedRestTemplate.exchange(openaiApiUrl, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
          int status = response.getStatusCode().value();
          throw new RuntimeException("HTTP " + status + ": " + response.getBody());
        }

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        return jsonNode.path("choices").get(0).path("message").path("content").asText();
      } catch (Exception e) {
        lastEx = new RuntimeException("LLM API 호출 실패: " + e.getMessage(), e);

        // 429 에러는 즉시 상위로 전파 (LLMQueueManager가 처리)
        if (e.getMessage() != null && e.getMessage().contains("429")) {
          throw new RuntimeException("429 Rate limit exceeded", e);
        }

        // 마지막 시도가 아니면 재시도
        if (attempt < Math.max(1, maxRetries)) {
          long backoff = (long) (initialBackoffMs * Math.pow(2, attempt - 1));
          long jitter = (long) (backoff * 0.2);
          long sleepMs = backoff + (long) (Math.random() * jitter);

          log.debug("재시도 대기: {}ms (시도 {}/{})", sleepMs, attempt, maxRetries);
          try {
            Thread.sleep(sleepMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw lastEx;
          }
        } else {
          break;
        }
      }
    }
    throw lastEx != null ? lastEx : new RuntimeException("LLM API 호출 실패");
  }

  /** Rate limit 상태 확인을 위한 health check */
  public boolean isHealthy() {
    try {
      log.debug("LLM API health check 시작");

      SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
      requestFactory.setConnectTimeout(connectTimeoutMs);
      requestFactory.setReadTimeout(readTimeoutMs);
      RestTemplate timedRestTemplate = new RestTemplate(requestFactory);

      // 최소한의 토큰으로 API 호출
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("Authorization", "Bearer " + openaiApiKey);

      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("model", openaiModel);
      requestBody.put("messages", Arrays.asList(Map.of("role", "user", "content", "1")));
      requestBody.put("max_tokens", 1); // 최소 응답 토큰

      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

      ResponseEntity<String> response =
          timedRestTemplate.exchange(openaiApiUrl, HttpMethod.POST, entity, String.class);

      boolean healthy = response.getStatusCode().is2xxSuccessful();
      if (healthy) {
        log.debug("LLM API health check 성공");
      }
      return healthy;

    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("429")) {
        log.debug("LLM API health check - Rate limit 여전히 활성");
      } else {
        log.debug("LLM API health check 실패: {}", e.getMessage());
      }
      return false;
    }
  }
}
