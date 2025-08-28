package com.yjlee.search.search.utils;

import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.yjlee.search.search.dto.AggregationBucketDto;
import com.yjlee.search.search.service.RRFScorer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * 검색 결과에서 Aggregation을 계산하는 유틸리티 클래스
 */
@UtilityClass
public class AggregationUtils {

  private static final int MAX_BUCKETS = 10;

  /**
   * Hit 리스트에서 카테고리/브랜드 aggregation 계산
   *
   * @param hits Elasticsearch Hit 리스트
   * @return 카테고리/브랜드별 집계 결과
   */
  public static Map<String, List<AggregationBucketDto>> calculateFromHits(
      List<Hit<JsonNode>> hits) {

    Map<String, Long> categoryCount = new HashMap<>();
    Map<String, Long> brandCount = new HashMap<>();

    for (Hit<JsonNode> hit : hits) {
      JsonNode source = hit.source();
      
      if (source != null) {
        extractAndCount(source, "category_name", categoryCount);
        extractAndCount(source, "brand_name", brandCount);
      }
    }

    return buildAggregationMap(categoryCount, brandCount);
  }

  /**
   * RRFResult 리스트에서 카테고리/브랜드 aggregation 계산
   *
   * @param results RRF 병합 결과 리스트
   * @return 카테고리/브랜드별 집계 결과
   */
  public static Map<String, List<AggregationBucketDto>> calculateFromRRFResults(
      List<RRFScorer.RRFResult> results) {

    Map<String, Long> categoryCount = new HashMap<>();
    Map<String, Long> brandCount = new HashMap<>();

    for (RRFScorer.RRFResult result : results) {
      JsonNode source = result.getDocument().source();
      
      if (source != null) {
        extractAndCount(source, "category_name", categoryCount);
        extractAndCount(source, "brand_name", brandCount);
      }
    }

    return buildAggregationMap(categoryCount, brandCount);
  }

  /**
   * JsonNode에서 필드 값을 추출하고 카운트
   */
  private static void extractAndCount(JsonNode source, String fieldName, Map<String, Long> countMap) {
    if (source.has(fieldName)) {
      String value = source.get(fieldName).asText();
      if (value != null && !value.isEmpty()) {
        countMap.merge(value, 1L, Long::sum);
      }
    }
  }

  /**
   * 카운트 맵을 AggregationBucketDto 리스트로 변환
   */
  private static List<AggregationBucketDto> convertToBuckets(Map<String, Long> countMap) {
    return countMap.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(MAX_BUCKETS)
        .map(e -> AggregationBucketDto.builder()
            .key(e.getKey())
            .docCount(e.getValue())
            .build())
        .collect(Collectors.toList());
  }

  /**
   * 카테고리/브랜드 카운트 맵을 최종 aggregation 맵으로 변환
   */
  private static Map<String, List<AggregationBucketDto>> buildAggregationMap(
      Map<String, Long> categoryCount, Map<String, Long> brandCount) {

    Map<String, List<AggregationBucketDto>> aggregations = new HashMap<>();
    
    // 카테고리 버킷 생성
    List<AggregationBucketDto> categoryBuckets = convertToBuckets(categoryCount);
    if (!categoryBuckets.isEmpty()) {
      aggregations.put("category", categoryBuckets);
    }
    
    // 브랜드 버킷 생성
    List<AggregationBucketDto> brandBuckets = convertToBuckets(brandCount);
    if (!brandBuckets.isEmpty()) {
      aggregations.put("brand", brandBuckets);
    }
    
    return aggregations;
  }
}