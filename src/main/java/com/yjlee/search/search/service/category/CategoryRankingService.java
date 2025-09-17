package com.yjlee.search.search.service.category;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.service.IndexEnvironmentService;
import com.yjlee.search.dictionary.category.model.CategoryMapping;
import com.yjlee.search.dictionary.category.model.CategoryRankingDictionary;
import com.yjlee.search.dictionary.category.repository.CategoryRankingDictionaryRepository;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextResponse;
import com.yjlee.search.dictionary.user.service.ElasticsearchAnalyzeService;
import jakarta.annotation.PostConstruct;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryRankingService {

  private final ElasticsearchAnalyzeService analyzeService;
  private final IndexEnvironmentService indexEnvironmentService;
  private final CategoryRankingDictionaryRepository categoryRankingDictionaryRepository;

  private final Cache<String, Map<String, List<CategoryWeight>>> versionedCache =
      Caffeine.newBuilder().maximumSize(5).build();

  @PostConstruct
  public void initializeCache() {
    log.info("카테고리 랭킹 캐시 초기화 시작");
    for (EnvironmentType envType : EnvironmentType.values()) {
      IndexEnvironment env = indexEnvironmentService.getEnvironmentOrNull(envType);
      if (env != null && env.getVersion() != null) {
        String version = env.getVersion();
        Map<String, List<CategoryWeight>> cache = loadFromDB(envType);
        versionedCache.put(version, cache);
        log.info(
            "카테고리 랭킹 캐시 로드 완료: env={}, version={}, keywords={}", envType, version, cache.size());
      }
    }
  }

  public Map<String, Integer> getCategoryWeights(String query, EnvironmentType environmentType) {
    if (query == null || query.trim().isEmpty()) {
      return Collections.emptyMap();
    }

    IndexEnvironment env = indexEnvironmentService.getEnvironment(environmentType);
    Map<String, List<CategoryWeight>> cache = versionedCache.getIfPresent(env.getVersion());
    if (cache == null) {
      log.warn("캐시 없음: version={}", env.getVersion());
      return Collections.emptyMap();
    }
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

  public void addVersionCache(String version, Map<String, List<CategoryWeight>> data) {
    versionedCache.put(version, data);
    log.info("카테고리 랭킹 캐시 추가: version={}, keywords={}", version, data.size());
  }

  public void refreshCache(EnvironmentType environmentType) {
    IndexEnvironment env = indexEnvironmentService.getEnvironment(environmentType);
    if (env != null && env.getVersion() != null) {
      Map<String, List<CategoryWeight>> cache = loadFromDB(environmentType);
      versionedCache.put(env.getVersion(), cache);
      log.info("카테고리 랭킹 캐시 갱신: env={}, version={}", environmentType, env.getVersion());
    }
  }

  private Map<String, List<CategoryWeight>> loadFromDB(EnvironmentType environmentType) {
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
        "카테고리 랭킹 DB 로드 완료 - 환경: {}, 키워드: {}개, 매핑: {}개",
        environmentType,
        cache.size(),
        totalMappings);

    return cache;
  }

  public void realtimeSync(EnvironmentType environment) {
    log.info("카테고리 랭킹 실시간 동기화 시작 - 환경: {}", environment);
    IndexEnvironment env = indexEnvironmentService.getEnvironment(environment);
    if (env != null && env.getVersion() != null) {
      versionedCache.invalidate(env.getVersion());
      refreshCache(environment);
    }
    log.info("카테고리 랭킹 실시간 동기화 완료 - 환경: {}", environment);
  }

  public void syncWithPreloadedData(
      List<CategoryRankingDictionary> categoryRankings, String version) {
    log.info("Preloaded 카테고리 랭킹 동기화 시작 - 버전: {}", version);

    Map<String, List<CategoryWeight>> cache = new HashMap<>();
    int totalMappings = 0;

    for (CategoryRankingDictionary dict : categoryRankings) {
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

    versionedCache.put(version, cache);
    log.info(
        "Preloaded 카테고리 랭킹 동기화 완료 - 버전: {}, 키워드: {}개, 매핑: {}개",
        version,
        cache.size(),
        totalMappings);
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
