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

  @Value("${openai.api.model:gpt-4.1-nano}")
  private String openaiModel;

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
      log.debug("LLM API 호출 시작 (custom temperature: {})", temperature);
      return performAPICall(prompt, temperature);
    } catch (Exception e) {
      log.error("LLM API 호출 실패: {}", e.getMessage(), e);
      throw new RuntimeException("LLM API 호출 실패: " + e.getMessage(), e);
    }
  }

  private String performAPICall(String prompt, Double temperature) throws Exception {
    // 연결/읽기 타임아웃을 적용한 로컬 RestTemplate 사용(추가 의존성 없이 java.net 기반)
    int connectTimeoutMs = 5000;
    int readTimeoutMs = 20000;
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
    double tempToUse = temperature != null ? temperature : 0.0;
    requestBody.put("temperature", tempToUse);

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
    ResponseEntity<String> response =
        timedRestTemplate.exchange(openaiApiUrl, HttpMethod.POST, entity, String.class);

    JsonNode jsonNode = objectMapper.readTree(response.getBody());
    return jsonNode.path("choices").get(0).path("message").path("content").asText();
  }
}
