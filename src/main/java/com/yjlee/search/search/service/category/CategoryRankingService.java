package com.yjlee.search.search.service.category;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.service.CategoryRankingDictionaryService;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryRankingService {

  private final CategoryRankingDictionaryService dictionaryService;
  private final Map<String, List<CategoryWeight>> cache = new ConcurrentHashMap<>();
  private volatile DictionaryEnvironmentType activeEnvironmentType =
      DictionaryEnvironmentType.CURRENT;

  @PostConstruct
  public void init() {
    loadCache(activeEnvironmentType);
  }

  public Map<String, Integer> getCategoryWeights(String query) {
    if (query == null || query.trim().isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, Integer> categoryWeights = new HashMap<>();
    String[] words = query.toLowerCase().split("\\s+");

    for (String word : words) {
      List<CategoryWeight> weights = cache.get(word);
      if (weights != null && !weights.isEmpty()) {
        for (CategoryWeight cw : weights) {
          categoryWeights.merge(cw.getCategory(), cw.getWeight(), Integer::sum);
        }
        log.debug("키워드 '{}' 매칭 - 카테고리 가중치: {}", word, weights);
      }
    }

    if (!categoryWeights.isEmpty()) {
      log.info("쿼리 '{}' - 적용된 카테고리 가중치: {}", query, categoryWeights);
    }

    return categoryWeights;
  }

  private void loadCache(DictionaryEnvironmentType environmentType) {
    try {
      log.info("카테고리 랭킹 사전 캐시 로딩 시작 - 환경: {}", environmentType);

      // 전체 리스트를 한 번에 가져와서 처리 (상세 API 호출 제거)
      var response = dictionaryService.getAllWithMappings(environmentType);

      cache.clear();
      int totalMappings = 0;

      if (response != null && response.getContent() != null) {
        for (var dict : response.getContent()) {
          String keyword = dict.getKeyword().toLowerCase();

          if (dict.getCategoryMappings() != null && !dict.getCategoryMappings().isEmpty()) {
            List<CategoryWeight> weights = new ArrayList<>();
            for (var mapping : dict.getCategoryMappings()) {
              weights.add(new CategoryWeight(mapping.getCategory(), mapping.getWeight()));
            }
            cache.put(keyword, weights);
            totalMappings += weights.size();
          }
        }
      }

      log.info(
          "카테고리 랭킹 사전 캐시 로딩 완료 - 환경: {}, 키워드: {}개, 매핑: {}개",
          environmentType,
          cache.size(),
          totalMappings);

    } catch (Exception e) {
      log.error("카테고리 랭킹 사전 캐시 로딩 실패", e);
    }
  }

  public void updateCacheRealtime(DictionaryEnvironmentType environmentType) {
    cache.clear();
    if (environmentType != null) {
      activeEnvironmentType = environmentType;
    } else {
      activeEnvironmentType = DictionaryEnvironmentType.CURRENT;
    }
    loadCache(activeEnvironmentType);
  }

  public String getCacheStatus() {
    int totalMappings = cache.values().stream().mapToInt(List::size).sum();
    return String.format(
        "Env: %s, Keywords: %d, Total mappings: %d",
        activeEnvironmentType.name(), cache.size(), totalMappings);
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
