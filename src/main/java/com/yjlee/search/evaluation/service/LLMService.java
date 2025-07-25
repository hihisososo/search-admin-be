package com.yjlee.search.evaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Value("${openai.api.url}")
  private String openaiApiUrl;

  @Value("${openai.api.key}")
  private String openaiApiKey;

  public String callLLMAPI(String prompt) {
    try {
      log.debug("🤖 LLM API 호출 시작");
      return performAPICall(prompt);
    } catch (Exception e) {
      log.error("❌ LLM API 호출 실패", e);
      return "";
    }
  }

  private String performAPICall(String prompt) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "Bearer " + openaiApiKey);

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("model", "gpt-4o-mini");
    requestBody.put("messages", Arrays.asList(Map.of("role", "user", "content", prompt)));
    requestBody.put("temperature", 0);

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
    ResponseEntity<String> response =
        restTemplate.exchange(openaiApiUrl, HttpMethod.POST, entity, String.class);

    JsonNode jsonNode = objectMapper.readTree(response.getBody());
    return jsonNode.path("choices").get(0).path("message").path("content").asText();
  }
}
