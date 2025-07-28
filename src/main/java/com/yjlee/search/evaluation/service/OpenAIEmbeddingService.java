package com.yjlee.search.evaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIEmbeddingService {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Value("${openai.api.embedding}")
  private String embeddingApiUrl;

  @Value("${openai.api.key}")
  private String openaiApiKey;

  public float[] getEmbedding(String text) {
    List<String> texts = List.of(text);
    List<float[]> embeddings = getBulkEmbeddings(texts);
    return embeddings.isEmpty() ? getEmptyEmbedding() : embeddings.get(0);
  }

  public List<float[]> getBulkEmbeddings(List<String> texts) {
    if (texts == null || texts.isEmpty()) {
      return new ArrayList<>();
    }

    // OpenAI API 제한 고려하여 청크 단위로 처리 (한 번에 최대 100개)
    final int CHUNK_SIZE = 100;
    List<float[]> allEmbeddings = new ArrayList<>();

    log.info("🤖 벌크 임베딩 생성 시작: {}개 텍스트 ({}개씩 청크 처리)", texts.size(), CHUNK_SIZE);

    for (int i = 0; i < texts.size(); i += CHUNK_SIZE) {
      int endIndex = Math.min(i + CHUNK_SIZE, texts.size());
      List<String> chunk = texts.subList(i, endIndex);

      try {
        log.debug("📦 청크 {}: {}-{}번째 텍스트 처리 중", (i / CHUNK_SIZE) + 1, i + 1, endIndex);
        List<float[]> chunkEmbeddings = processChunk(chunk);
        allEmbeddings.addAll(chunkEmbeddings);

        // API rate limit 방지를 위한 간단한 대기
        if (endIndex < texts.size()) {
          Thread.sleep(100); // 100ms 대기
        }

      } catch (Exception e) {
        log.error("❌ 청크 {}번 처리 실패: {}-{}번째", (i / CHUNK_SIZE) + 1, i + 1, endIndex, e);

        // 실패한 청크는 빈 임베딩으로 대체
        for (int j = 0; j < chunk.size(); j++) {
          allEmbeddings.add(getEmptyEmbedding());
        }
      }
    }

    log.info("✅ 벌크 임베딩 생성 완료: {}개", allEmbeddings.size());
    return allEmbeddings;
  }

  private List<float[]> processChunk(List<String> chunk) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Authorization", "Bearer " + openaiApiKey);

    // null이나 빈 텍스트 필터링
    List<String> validTexts =
        chunk.stream()
            .map(text -> text == null || text.trim().isEmpty() ? " " : text)
            .collect(Collectors.toList());

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("model", "text-embedding-3-small");
    requestBody.put("input", validTexts);

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
    ResponseEntity<String> response =
        restTemplate.exchange(embeddingApiUrl, HttpMethod.POST, entity, String.class);

    JsonNode jsonNode = objectMapper.readTree(response.getBody());
    JsonNode dataArray = jsonNode.path("data");

    List<float[]> embeddings = new ArrayList<>();
    for (int i = 0; i < dataArray.size(); i++) {
      JsonNode embeddingArray = dataArray.get(i).path("embedding");
      float[] embedding = new float[embeddingArray.size()];
      for (int j = 0; j < embeddingArray.size(); j++) {
        embedding[j] = (float) embeddingArray.get(j).asDouble();
      }
      embeddings.add(embedding);
    }

    return embeddings;
  }

  private float[] getEmptyEmbedding() {
    float[] emptyEmbedding = new float[1536];
    for (int i = 0; i < 1536; i++) {
      emptyEmbedding[i] = 0.0f;
    }
    return emptyEmbedding;
  }
}
