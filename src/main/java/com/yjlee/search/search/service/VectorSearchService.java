package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.embedding.service.EmbeddingService;
import com.yjlee.search.embedding.service.EmbeddingService.EmbeddingType;
import com.yjlee.search.search.constants.SearchConstants;
import com.yjlee.search.search.constants.VectorSearchConstants;
import com.yjlee.search.search.dto.VectorSearchConfig;
import com.yjlee.search.search.dto.VectorSearchResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

  @Value("${app.evaluation.candidate.min-score:0.60}")
  private double defaultVectorMinScore;

  private final EmbeddingService embeddingService;
  private final ElasticsearchClient elasticsearchClient;

  // Thread-safe LRU 캐시
  private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();
  private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

  /** 쿼리 임베딩 생성 (캐싱 포함) */
  public float[] getQueryEmbedding(String query) {
    // Read lock으로 캐시 체크
    cacheLock.readLock().lock();
    try {
      float[] cached = embeddingCache.get(query);
      if (cached != null) {
        return cached;
      }
    } finally {
      cacheLock.readLock().unlock();
    }

    // 캐시에 없으면 생성
    log.debug("Generating embedding for query: {}", query);
    float[] embedding = embeddingService.getEmbedding(query, EmbeddingType.QUERY);

    // Write lock으로 캐시에 저장
    cacheLock.writeLock().lock();
    try {
      // LRU: 캐시 크기 초과시 가장 오래된 항목 제거
      if (embeddingCache.size() >= SearchConstants.EMBEDDING_CACHE_SIZE) {
        String oldestKey = embeddingCache.keySet().iterator().next();
        embeddingCache.remove(oldestKey);
      }
      embeddingCache.put(query, embedding);
    } finally {
      cacheLock.writeLock().unlock();
    }
    
    return embedding;
  }

  /** 다중 필드 KNN 벡터 검색 실행 (기본 설정) */
  public SearchResponse<JsonNode> multiFieldVectorSearch(String index, String query, int topK) {
    VectorSearchConfig config =
        VectorSearchConfig.builder().topK(topK).vectorMinScore(defaultVectorMinScore).build();
    return multiFieldVectorSearch(index, query, config);
  }

  /** 다중 필드 KNN 벡터 검색 실행 (설정 객체 사용) - SearchRequest 포함 반환 */
  public VectorSearchResult multiFieldVectorSearchWithRequest(
      String index, String query, VectorSearchConfig config) {
    try {
      float[] queryVector = getQueryEmbedding(query);
      int topK = config.getTopK();

      List<Float> queryVectorList = new ArrayList<>();
      for (float f : queryVector) {
        queryVectorList.add(f);
      }

      // 벡터 검색 설정
      double minScore =
          config.getVectorMinScore() != null ? config.getVectorMinScore() : defaultVectorMinScore;

      // sub_searches를 사용한 다중 벡터 필드 검색
      SearchRequest.Builder searchBuilder =
          new SearchRequest.Builder()
              .index(index)
              .size(topK)
              .minScore(minScore)
              .knn(
                  k -> {
                    var knnBuilder =
                        k.field(VectorSearchConstants.NAME_VECTOR_FIELD)
                            .queryVector(queryVectorList)
                            .k(topK)
                            .numCandidates(config.getNumCandidates())
                            .boost(config.getNameVectorBoost());

                    // KNN 필터 적용
                    if (config.getFilterQueries() != null && !config.getFilterQueries().isEmpty()) {
                      knnBuilder.filter(config.getFilterQueries());
                    }
                    return knnBuilder;
                  })
              .knn(
                  k -> {
                    var knnBuilder =
                        k.field(VectorSearchConstants.SPECS_VECTOR_FIELD)
                            .queryVector(queryVectorList)
                            .k(topK)
                            .numCandidates(config.getNumCandidates())
                            .boost(config.getSpecsVectorBoost());

                    // KNN 필터 적용
                    if (config.getFilterQueries() != null && !config.getFilterQueries().isEmpty()) {
                      knnBuilder.filter(config.getFilterQueries());
                    }
                    return knnBuilder;
                  })
              .source(
                  src ->
                      src.filter(
                          f -> f.excludes(VectorSearchConstants.getVectorFieldsToExclude())));

      SearchRequest searchRequest = searchBuilder.build();

      log.debug(
          "Executing multi-field vector search - query: {}, topK: {}, minScore: {}",
          query,
          topK,
          minScore);

      SearchResponse<JsonNode> response = elasticsearchClient.search(searchRequest, JsonNode.class);

      return VectorSearchResult.builder().response(response).request(searchRequest).build();

    } catch (IOException e) {
      log.error("Multi-field vector search failed", e);
      throw new RuntimeException("Vector search failed", e);
    }
  }

  /** 다중 필드 KNN 벡터 검색 실행 (설정 객체 사용) */
  public SearchResponse<JsonNode> multiFieldVectorSearch(
      String index, String query, VectorSearchConfig config) {
    try {
      float[] queryVector = getQueryEmbedding(query);
      int topK = config.getTopK();

      List<Float> queryVectorList = new ArrayList<>();
      for (float f : queryVector) {
        queryVectorList.add(f);
      }

      // 벡터 검색 설정
      double minScore =
          config.getVectorMinScore() != null ? config.getVectorMinScore() : defaultVectorMinScore;

      // sub_searches를 사용한 다중 벡터 필드 검색
      SearchRequest.Builder searchBuilder =
          new SearchRequest.Builder()
              .index(index)
              .size(topK)
              .minScore(minScore)
              .knn(
                  k -> {
                    var knnBuilder =
                        k.field(VectorSearchConstants.NAME_VECTOR_FIELD)
                            .queryVector(queryVectorList)
                            .k(topK)
                            .numCandidates(config.getNumCandidates())
                            .boost(config.getNameVectorBoost());

                    // KNN 필터 적용
                    if (config.getFilterQueries() != null && !config.getFilterQueries().isEmpty()) {
                      knnBuilder.filter(config.getFilterQueries());
                    }
                    return knnBuilder;
                  })
              .knn(
                  k -> {
                    var knnBuilder =
                        k.field(VectorSearchConstants.SPECS_VECTOR_FIELD)
                            .queryVector(queryVectorList)
                            .k(topK)
                            .numCandidates(config.getNumCandidates())
                            .boost(config.getSpecsVectorBoost());

                    // KNN 필터 적용
                    if (config.getFilterQueries() != null && !config.getFilterQueries().isEmpty()) {
                      knnBuilder.filter(config.getFilterQueries());
                    }
                    return knnBuilder;
                  })
              .source(
                  src ->
                      src.filter(
                          f -> f.excludes(VectorSearchConstants.getVectorFieldsToExclude())));

      SearchRequest searchRequest = searchBuilder.build();

      log.debug(
          "Executing multi-field vector search - query: {}, topK: {}, minScore: {}",
          query,
          topK,
          minScore);

      return elasticsearchClient.search(searchRequest, JsonNode.class);

    } catch (IOException e) {
      log.error("Multi-field vector search failed", e);
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

  /** 기본 벡터 최소 점수 반환 */
  public double getDefaultVectorMinScore() {
    return defaultVectorMinScore;
  }
}
