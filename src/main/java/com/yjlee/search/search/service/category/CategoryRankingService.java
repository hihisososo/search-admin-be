package com.yjlee.search.search.service.category;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.service.CategoryRankingDictionaryService;
import com.yjlee.search.dictionary.user.service.ElasticsearchAnalyzeService;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextResponse;
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
  private final ElasticsearchAnalyzeService analyzeService;
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
    Set<String> appliedKeywords = new HashSet<>();  // 이미 적용된 키워드 추적
    
    // 1. 먼저 공백 단위로 매칭
    String[] words = query.toLowerCase().split("\\s+");
    for (String word : words) {
      if (appliedKeywords.contains(word)) {
        continue;  // 이미 적용된 키워드는 스킵
      }
      
      List<CategoryWeight> weights = cache.get(word);
      if (weights != null && !weights.isEmpty()) {
        for (CategoryWeight cw : weights) {
          categoryWeights.merge(cw.getCategory(), cw.getWeight(), Integer::sum);
        }
        appliedKeywords.add(word);  // 적용된 키워드로 마킹
        log.debug("키워드 '{}' 매칭 (공백 단위) - 카테고리 가중치: {}", word, weights);
      }
    }
    
    // 2. nori 형태소 분석 결과로도 매칭
    try {
      List<AnalyzeTextResponse.TokenInfo> tokens = analyzeService.analyzeText(query, activeEnvironmentType);
      for (AnalyzeTextResponse.TokenInfo tokenInfo : tokens) {
        String token = tokenInfo.getToken().toLowerCase();
        
        if (appliedKeywords.contains(token)) {
          continue;  // 이미 적용된 키워드는 스킵
        }
        
        List<CategoryWeight> weights = cache.get(token);
        if (weights != null && !weights.isEmpty()) {
          for (CategoryWeight cw : weights) {
            categoryWeights.merge(cw.getCategory(), cw.getWeight(), Integer::sum);
          }
          appliedKeywords.add(token);  // 적용된 키워드로 마킹
          log.debug("키워드 '{}' 매칭 (형태소 분석) - 카테고리 가중치: {}", token, weights);
        }
      }
    } catch (Exception e) {
      log.warn("형태소 분석 중 오류 발생, 공백 단위 매칭만 사용: {}", e.getMessage());
    }

    if (!categoryWeights.isEmpty()) {
      log.info("쿼리 '{}' - 적용된 카테고리 가중치: {}, 적용된 키워드: {}", 
          query, categoryWeights, appliedKeywords);
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
