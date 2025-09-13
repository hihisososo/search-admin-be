package com.yjlee.search.search.service.category;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextResponse;
import com.yjlee.search.dictionary.user.service.ElasticsearchAnalyzeService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryRankingService {

  private final CategoryRankingCacheLoader cacheLoader;
  private final ElasticsearchAnalyzeService analyzeService;

  // 기본 환경(CURRENT)로 가중치 조회
  public Map<String, Integer> getCategoryWeights(String query) {
    return getCategoryWeights(query, EnvironmentType.CURRENT);
  }

  // 환경별 가중치 조회
  public Map<String, Integer> getCategoryWeights(String query, EnvironmentType environmentType) {
    if (query == null || query.trim().isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, List<CategoryWeight>> cache = cacheLoader.loadCache(environmentType);
    Map<String, Integer> categoryWeights = new HashMap<>();
    Set<String> appliedKeywords = new HashSet<>();

    // 1. 먼저 공백 단위로 매칭
    String[] words = query.toLowerCase().split("\\s+");
    for (String word : words) {
      if (appliedKeywords.contains(word)) {
        continue;
      }

      List<CategoryWeight> weights = cache.get(word);
      if (weights != null && !weights.isEmpty()) {
        for (CategoryWeight cw : weights) {
          categoryWeights.merge(cw.getCategory(), cw.getWeight(), Integer::sum);
        }
        appliedKeywords.add(word);
        log.debug("키워드 '{}' 매칭 (공백 단위) - 카테고리 가중치: {}", word, weights);
      }
    }

    // 2. nori 형태소 분석 결과로도 매칭
    try {
      List<AnalyzeTextResponse.TokenInfo> tokens =
          analyzeService.analyzeText(query, environmentType);
      for (AnalyzeTextResponse.TokenInfo tokenInfo : tokens) {
        String token = tokenInfo.getToken().toLowerCase();

        if (appliedKeywords.contains(token)) {
          continue;
        }

        List<CategoryWeight> weights = cache.get(token);
        if (weights != null && !weights.isEmpty()) {
          for (CategoryWeight cw : weights) {
            categoryWeights.merge(cw.getCategory(), cw.getWeight(), Integer::sum);
          }
          appliedKeywords.add(token);
          log.debug("키워드 '{}' 매칭 (형태소 분석) - 카테고리 가중치: {}", token, weights);
        }
      }
    } catch (Exception e) {
      log.warn("형태소 분석 중 오류 발생, 공백 단위 매칭만 사용: {}", e.getMessage());
    }

    if (!categoryWeights.isEmpty()) {
      log.info(
          "쿼리 '{}' - 환경: {} - 적용된 카테고리 가중치: {}, 적용된 키워드: {}",
          query,
          environmentType,
          categoryWeights,
          appliedKeywords);
    }

    return categoryWeights;
  }

  public void updateCacheRealtime(EnvironmentType environmentType) {
    cacheLoader.evictCache(environmentType);
  }

  public void clearAllCache() {
    cacheLoader.evictAllCache();
  }

  public static class CategoryWeight {
    private final String category;
    private final Integer weight;

    public CategoryWeight(String category, Integer weight) {
      this.category = category;
      this.weight = weight != null ? weight : 1000;
    }

    public String getCategory() {
      return category;
    }

    public Integer getWeight() {
      return weight;
    }

    @Override
    public String toString() {
      return String.format("{category='%s', weight=%d}", category, weight);
    }
  }
}
