package com.yjlee.search.embedding.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "embedding.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAIEmbeddingServiceImpl implements EmbeddingService {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Value("${openai.api.embedding}")
  private String embeddingApiUrl;

  @Value("${openai.api.key}")
  private String openaiApiKey;

  @Value("${openai.embedding.model:text-embedding-3-large}")
  private String modelName;

  @Value("${openai.embedding.dimension:1536}")
  private int embeddingDimension;

  @Override
  public float[] getEmbedding(String text) {
    return getEmbedding(text, EmbeddingType.DOCUMENT);
  }

  @Override
  public float[] getEmbedding(String text, EmbeddingType type) {
    List<String> texts = List.of(text);
    List<float[]> embeddings = getBulkEmbeddings(texts, type);
    return embeddings.isEmpty() ? getEmptyEmbedding() : embeddings.get(0);
  }

  @Override
  public List<float[]> getBulkEmbeddings(List<String> texts) {
    return getBulkEmbeddings(texts, EmbeddingType.DOCUMENT);
  }

  @Override
  public List<float[]> getBulkEmbeddings(List<String> texts, EmbeddingType type) {
    if (texts == null || texts.isEmpty()) {
      return new ArrayList<>();
    }

    final int CHUNK_SIZE = 100;
    List<float[]> allEmbeddings = new ArrayList<>();

    log.info("🤖 OpenAI 임베딩 생성 시작: {}개 텍스트 (모델: {})", texts.size(), modelName);

    for (int i = 0; i < texts.size(); i += CHUNK_SIZE) {
      int endIndex = Math.min(i + CHUNK_SIZE, texts.size());
      List<String> chunk = texts.subList(i, endIndex);

      try {
        log.debug("📦 청크 {}: {}-{}번째 텍스트 처리 중", (i / CHUNK_SIZE) + 1, i + 1, endIndex);
        List<float[]> chunkEmbeddings = processChunk(chunk);
        allEmbeddings.addAll(chunkEmbeddings);

        if (endIndex < texts.size()) {
          Thread.sleep(100);
        }

      } catch (Exception e) {
        log.error("❌ 청크 {}번 처리 실패: {}-{}번째", (i / CHUNK_SIZE) + 1, i + 1, endIndex, e);
        for (int j = 0; j < chunk.size(); j++) {
          allEmbeddings.add(getEmptyEmbedding());
        }
      }
    }

    log.info("✅ OpenAI 임베딩 생성 완료: {}개", allEmbeddings.size());
    return allEmbeddings;
  }

  private List<float[]> processChunk(List<String> chunk) throws Exception {
    while (true) {
      try {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openaiApiKey);

        List<String> validTexts =
            chunk.stream()
                .map(text -> text == null || text.trim().isEmpty() ? " " : text)
                .collect(Collectors.toList());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
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

      } catch (HttpClientErrorException e) {
        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
          log.warn("⚠️ Rate limit 도달 (429), 30초 대기 후 재시도");
          Thread.sleep(30000);
        } else {
          throw e;
        }
      } catch (Exception e) {
        if (e.getMessage() != null && e.getMessage().contains("429")) {
          log.warn("⚠️ Rate limit 도달, 30초 대기 후 재시도");
          Thread.sleep(30000);
        } else {
          throw e;
        }
      }
    }
  }

  @Override
  public String getModelName() {
    return modelName;
  }

  @Override
  public int getEmbeddingDimension() {
    return embeddingDimension;
  }

  private float[] getEmptyEmbedding() {
    float[] emptyEmbedding = new float[embeddingDimension];
    Arrays.fill(emptyEmbedding, 0.0f);
    return emptyEmbedding;
  }
}
