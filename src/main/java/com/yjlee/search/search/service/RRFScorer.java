package com.yjlee.search.search.service;

import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RRFScorer {

  /**
   * RRF (Reciprocal Rank Fusion) 알고리즘으로 두 검색 결과를 병합
   *
   * @param bm25Results BM25 키워드 검색 결과
   * @param vectorResults 벡터 검색 결과
   * @param k RRF 상수 (기본값 60)
   * @param finalSize 최종 반환할 결과 크기
   * @param bm25Weight BM25 가중치 (0.0~1.0)
   * @return 병합된 검색 결과
   */
  public List<RRFResult> mergeWithRRF(
      List<Hit<JsonNode>> bm25Results,
      List<Hit<JsonNode>> vectorResults,
      int k,
      int finalSize,
      double bm25Weight) {

    Map<String, RRFResult> scoreMap = new HashMap<>();
    double vectorWeight = 1.0 - bm25Weight;

    // BM25 결과 처리
    for (int rank = 0; rank < bm25Results.size(); rank++) {
      Hit<JsonNode> hit = bm25Results.get(rank);
      String docId = hit.id();
      double rrfScore = 1.0 / (rank + 1 + k);

      RRFResult result =
          scoreMap.computeIfAbsent(docId, id -> new RRFResult(id, hit, bm25Weight, vectorWeight));
      result.setBm25RrfScore(rrfScore);
      result.setBm25Rank(rank + 1);
      result.setOriginalBm25Score(hit.score() != null ? hit.score() : 0.0);

      log.trace("BM25 - DocId: {}, Rank: {}, RRF Score: {:.4f}", docId, rank + 1, rrfScore);
    }

    // Vector 결과 처리
    for (int rank = 0; rank < vectorResults.size(); rank++) {
      Hit<JsonNode> hit = vectorResults.get(rank);
      String docId = hit.id();
      double rrfScore = 1.0 / (rank + 1 + k);

      RRFResult result =
          scoreMap.computeIfAbsent(docId, id -> new RRFResult(id, hit, bm25Weight, vectorWeight));
      result.setVectorRrfScore(rrfScore);
      result.setVectorRank(rank + 1);
      result.setOriginalVectorScore(hit.score() != null ? hit.score() : 0.0);

      log.trace("Vector - DocId: {}, Rank: {}, RRF Score: {:.4f}", docId, rank + 1, rrfScore);
    }

    // 최종 점수로 정렬
    List<RRFResult> sortedResults =
        scoreMap.values().stream()
            .sorted((a, b) -> Double.compare(b.getTotalRrfScore(), a.getTotalRrfScore()))
            .limit(finalSize)
            .collect(Collectors.toList());

    // 로깅
    if (log.isDebugEnabled()) {
      log.debug("RRF Merge completed:");
      log.debug("  - BM25 results: {} (weight: {})", bm25Results.size(), bm25Weight);
      log.debug("  - Vector results: {} (weight: {})", vectorResults.size(), vectorWeight);
      log.debug("  - Unique documents: {}", scoreMap.size());
      log.debug("  - Final results: {}", sortedResults.size());

      if (!sortedResults.isEmpty()) {
        log.debug("  - Top 3 results:");
        sortedResults.stream()
            .limit(3)
            .forEach(
                r ->
                    log.debug(
                        "    {} - Total RRF: {} (BM25: rank={}, Vector: rank={})",
                        r.getId(),
                        r.getTotalRrfScore(),
                        r.getBm25Rank(),
                        r.getVectorRank()));
      }
    }

    return sortedResults;
  }

  /** RRF 결과 클래스 */
  @Data
  public static class RRFResult {
    private final String id;
    private final Hit<JsonNode> document;
    private final double bm25Weight;
    private final double vectorWeight;

    private double bm25RrfScore = 0.0;
    private double vectorRrfScore = 0.0;
    private Integer bm25Rank = null;
    private Integer vectorRank = null;

    // 원본 점수 (디버깅용)
    private double originalBm25Score = 0.0;
    private double originalVectorScore = 0.0;

    public RRFResult(String id, Hit<JsonNode> document, double bm25Weight, double vectorWeight) {
      this.id = id;
      this.document = document;
      this.bm25Weight = bm25Weight;
      this.vectorWeight = vectorWeight;
    }

    /** 총 RRF 점수 계산 (가중치 적용) */
    public double getTotalRrfScore() {
      return (bm25RrfScore * bm25Weight) + (vectorRrfScore * vectorWeight);
    }

    /** 하이브리드 점수 설명 생성 (디버깅용) */
    public Map<String, Object> getScoreExplanation() {
      Map<String, Object> explanation = new HashMap<>();
      explanation.put("totalRrfScore", getTotalRrfScore());
      explanation.put("bm25RrfScore", bm25RrfScore);
      explanation.put("vectorRrfScore", vectorRrfScore);
      explanation.put("bm25Weight", bm25Weight);
      explanation.put("vectorWeight", vectorWeight);
      explanation.put("weightedBm25Score", bm25RrfScore * bm25Weight);
      explanation.put("weightedVectorScore", vectorRrfScore * vectorWeight);
      explanation.put("bm25Rank", bm25Rank);
      explanation.put("vectorRank", vectorRank);
      explanation.put("originalBm25Score", originalBm25Score);
      explanation.put("originalVectorScore", originalVectorScore);
      return explanation;
    }
  }
}
