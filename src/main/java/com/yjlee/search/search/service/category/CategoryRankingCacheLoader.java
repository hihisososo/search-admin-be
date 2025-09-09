package com.yjlee.search.search.service.category;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.model.CategoryMapping;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionaryRepository;
import com.yjlee.search.search.service.category.CategoryRankingService.CategoryWeight;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryRankingCacheLoader {

  private final CategoryRankingDictionaryRepository categoryRankingDictionaryRepository;

  @Cacheable(value = "categoryRanking", key = "#environmentType")
  public Map<String, List<CategoryWeight>> loadCache(DictionaryEnvironmentType environmentType) {
    log.info("카테고리 랭킹 사전 캐시 로딩 - 환경: {}", environmentType);

    List<CategoryRankingDictionary> dictionaries =
        categoryRankingDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environmentType);

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
  public void evictCache(DictionaryEnvironmentType environmentType) {
    log.info("카테고리 랭킹 캐시 클리어 - 환경: {}", environmentType);
  }

  @CacheEvict(value = "categoryRanking", allEntries = true)
  public void evictAllCache() {
    log.info("카테고리 랭킹 전체 캐시 클리어");
  }
}
