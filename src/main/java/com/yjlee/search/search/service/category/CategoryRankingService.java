package com.yjlee.search.search.service.category;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.service.CategoryRankingDictionaryService;
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

  public Map<String, Integer> getCategoryWeights(String query) {
    if (query == null || query.trim().isEmpty()) {
      return Collections.emptyMap();
    }

    if (cache.isEmpty()) {
      loadCache(activeEnvironmentType);
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

      var response = dictionaryService.getList(0, 10000, null, "keyword", "asc", environmentType);

      cache.clear();
      int totalMappings = 0;

      response
          .getContent()
          .forEach(
              dict -> {
                String keyword = dict.getKeyword().toLowerCase();

                try {
                  var detailResponse =
                      dictionaryService.getByKeyword(dict.getKeyword(), environmentType);

                  if (detailResponse != null && detailResponse.getCategoryMappings() != null) {
                    List<CategoryWeight> weights = new ArrayList<>();
                    detailResponse
                        .getCategoryMappings()
                        .forEach(
                            mapping -> {
                              weights.add(
                                  new CategoryWeight(mapping.getCategory(), mapping.getWeight()));
                            });

                    if (!weights.isEmpty()) {
                      cache.put(keyword, weights);
                    }
                  }
                } catch (Exception e) {
                  log.warn("키워드 '{}' 상세 조회 실패: {}", dict.getKeyword(), e.getMessage());
                }
              });

      totalMappings = cache.values().stream().mapToInt(List::size).sum();
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
