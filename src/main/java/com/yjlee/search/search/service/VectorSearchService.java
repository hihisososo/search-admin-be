package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.evaluation.service.OpenAIEmbeddingService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

  private final OpenAIEmbeddingService embeddingService;
  private final ElasticsearchClient elasticsearchClient;

  // LRU 캐시 - 최대 1000개 항목 유지
  private final Map<String, float[]> embeddingCache =
      Collections.synchronizedMap(
          new LinkedHashMap<String, float[]>(1001, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
              return size() > 1000;
            }
          });

  /** 쿼리 임베딩 생성 (캐싱 포함) */
  public float[] getQueryEmbedding(String query) {
    // 캐시 체크
    float[] cached = embeddingCache.get(query);
    if (cached != null) {
      return cached;
    }

    // 캐시에 없으면 생성 (락 밖에서)
    log.debug("Generating embedding for query: {}", query);
    float[] embedding = embeddingService.getEmbedding(query);

    // 캐시에 저장
    embeddingCache.put(query, embedding);
    return embedding;
  }

  /** KNN 벡터 검색 실행 */
  public List<Hit<JsonNode>> searchByVector(
      String index, String query, int k, int numCandidates, double minScore) {
    try {
      float[] queryVector = getQueryEmbedding(query);

      // float[]를 List<Float>로 변환
      List<Float> queryVectorList = new ArrayList<>();
      for (float f : queryVector) {
        queryVectorList.add(f);
      }

      // KNN 검색 쿼리 구성
      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(index)
                      .size(k)
                      .minScore(minScore)
                      .query(
                          q ->
                              q.knn(
                                  knn ->
                                      knn.field("name_specs_vector")
                                          .queryVector(queryVectorList)
                                          .k(k)
                                          .numCandidates(numCandidates)))
                      // 벡터 필드 제외하여 응답 크기 최소화
                      .source(src -> src.filter(f -> f.excludes("name_specs_vector"))));

      log.debug("Executing vector search for query: {}, k: {}, minScore: {}", query, k, minScore);
      SearchResponse<JsonNode> response = elasticsearchClient.search(searchRequest, JsonNode.class);

      List<Hit<JsonNode>> hits = response.hits().hits();
      log.info("Vector search completed. Found {} results", hits.size());

      return hits;

    } catch (IOException e) {
      log.error("Vector search failed for query: {}", query, e);
      return new ArrayList<>();
    } catch (Exception e) {
      log.error("Unexpected error in vector search", e);
      return new ArrayList<>();
    }
  }

  /** 벡터 검색만 실행 (VECTOR_ONLY 모드용) - 300개 가져오기 */
  public SearchResponse<JsonNode> vectorOnlySearch(
      String index, String query, int topK, double minScore) {
    try {
      float[] queryVector = getQueryEmbedding(query);

      List<Float> queryVectorList = new ArrayList<>();
      for (float f : queryVector) {
        queryVectorList.add(f);
      }

      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(index)
                      .size(topK) // 전체 TopK 개수만큼 가져오기
                      .minScore(minScore)
                      .query(
                          q ->
                              q.knn(
                                  knn ->
                                      knn.field("name_specs_vector")
                                          .queryVector(queryVectorList)
                                          .k(topK)
                                          .numCandidates(topK * 3) // 일반적으로 k의 3배 정도
                                  ))
                      .source(src -> src.filter(f -> f.excludes("name_specs_vector"))));

      log.debug(
          "Executing vector-only search for query: {}, topK: {}, minScore: {}",
          query,
          topK,
          minScore);
      return elasticsearchClient.search(searchRequest, JsonNode.class);

    } catch (IOException e) {
      log.error("Vector-only search failed", e);
      throw new RuntimeException("Vector search failed", e);
    }
  }

  /** 캐시 초기화 (메모리 관리용) */
  public void clearCache() {
    embeddingCache.clear();
    log.info("Embedding cache cleared");
  }

  /** 캐시 크기 확인 */
  public int getCacheSize() {
    return embeddingCache.size();
  }
}
