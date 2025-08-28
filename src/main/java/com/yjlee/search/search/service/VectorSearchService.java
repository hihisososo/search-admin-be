package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.evaluation.service.OpenAIEmbeddingService;
import com.yjlee.search.search.constants.VectorSearchConstants;
import com.yjlee.search.search.dto.VectorSearchConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /** 다중 필드 KNN 벡터 검색 실행 (기본 설정) */
  public SearchResponse<JsonNode> multiFieldVectorSearch(String index, String query, int topK) {
    VectorSearchConfig config =
        VectorSearchConfig.builder().topK(topK).vectorMinScore(defaultVectorMinScore).build();
    return multiFieldVectorSearch(index, query, config);
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
      SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(index)
                      .size(topK)
                      .minScore(minScore)
                      .knn(
                          k ->
                              k.field(VectorSearchConstants.NAME_VECTOR_FIELD)
                                  .queryVector(queryVectorList)
                                  .k(topK)
                                  .numCandidates(config.getNumCandidates())
                                  .boost(config.getNameVectorBoost()))
                      .knn(
                          k ->
                              k.field(VectorSearchConstants.SPECS_VECTOR_FIELD)
                                  .queryVector(queryVectorList)
                                  .k(topK)
                                  .numCandidates(config.getNumCandidates())
                                  .boost(config.getSpecsVectorBoost()))
                      .source(
                          src ->
                              src.filter(
                                  f ->
                                      f.excludes(
                                          VectorSearchConstants.getVectorFieldsToExclude()))));

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
}
