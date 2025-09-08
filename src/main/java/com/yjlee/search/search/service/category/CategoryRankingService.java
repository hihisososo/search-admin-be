package com.yjlee.search.search.service.category;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.model.CategoryMapping;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionaryRepository;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextResponse;
import com.yjlee.search.dictionary.user.service.ElasticsearchAnalyzeService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryRankingService {

  private final CategoryRankingDictionaryRepository categoryRankingDictionaryRepository;
  private final ElasticsearchAnalyzeService analyzeService;

  // 기본 환경(CURRENT)로 가중치 조회
  public Map<String, Integer> getCategoryWeights(String query) {
    return getCategoryWeights(query, DictionaryEnvironmentType.CURRENT);
  }

  // 환경별 가중치 조회
  public Map<String, Integer> getCategoryWeights(
      String query, DictionaryEnvironmentType environmentType) {
    if (query == null || query.trim().isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, List<CategoryWeight>> cache = loadCategoryCache(environmentType);
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

  @Cacheable(value = "categoryRanking", key = "#environmentType")
  public Map<String, List<CategoryWeight>> loadCategoryCache(
      DictionaryEnvironmentType environmentType) {
    log.info("카테고리 랭킹 사전 캐시 로딩 - 환경: {}", environmentType);

    List<CategoryRankingDictionary> dictionaries =
        categoryRankingDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(
            environmentType);

    Map<String, List<CategoryWeight>> cache = new HashMap<>();
    int totalMappings = 0;

    for (CategoryRankingDictionary dict : dictionaries) {
      String keyword = dict.getKeyword().toLowerCase();

      if (dict.getCategoryMappings() != null && !dict.getCategoryMappings().isEmpty()) {
        List<CategoryWeight> weights = new ArrayList<>();
        for (CategoryMapping mapping : dict.getCategoryMappings()) {
          weights.add(new CategoryWeight(mapping.getCategory(), mapping.getWeight()));
        }
        cache.put(keyword, weights);
        totalMappings += weights.size();
      }
    }

    log.info(
        "카테고리 랭킹 사전 캐시 로딩 완료 - 환경: {}, 키워드: {}개, 매핑: {}개",
        environmentType,
        cache.size(),
        totalMappings);

    return cache;
  }

  @CacheEvict(value = "categoryRanking", key = "#environmentType")
  public void updateCacheRealtime(DictionaryEnvironmentType environmentType) {
    log.info("카테고리 랭킹 캐시 클리어 - 환경: {}", environmentType);
    // 캐시 제거만 하면 다음 요청 시 자동으로 재로드됨
  }

  @CacheEvict(value = "categoryRanking", allEntries = true)
  public void clearAllCache() {
    log.info("모든 카테고리 랭킹 캐시 클리어");
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
