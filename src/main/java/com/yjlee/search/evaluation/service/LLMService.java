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
          if (status == 429 || (status >= 500 && status < 600)) {
            throw new RuntimeException("HTTP " + status);
          }
          throw new RuntimeException("HTTP " + status + ": " + response.getBody());
        }

        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        return jsonNode.path("choices").get(0).path("message").path("content").asText();
      } catch (Exception e) {
        lastEx = new RuntimeException("LLM API 호출 실패: " + e.getMessage(), e);
        if (attempt >= Math.max(1, maxRetries)) break;
        long backoff = (long) (initialBackoffMs * Math.pow(2, attempt - 1));
        long jitter = (long) (backoff * 0.2);
        long sleepMs = backoff + (long) (Math.random() * jitter);
        try {
          Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw lastEx;
        }
      }
    }
    throw lastEx != null ? lastEx : new RuntimeException("LLM API 호출 실패");
  }
}
