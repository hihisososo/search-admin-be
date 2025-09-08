package com.yjlee.search.search.service.category;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.service.CategoryRankingDictionaryService;
import com.yjlee.search.dictionary.user.dto.AnalyzeTextResponse;
import com.yjlee.search.dictionary.user.service.ElasticsearchAnalyzeService;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryRankingService {

  private final CategoryRankingDictionaryService dictionaryService;
  private final ElasticsearchAnalyzeService analyzeService;

  // 환경별 캐시 맵
  private final Map<DictionaryEnvironmentType, Map<String, List<CategoryWeight>>>
      environmentCaches = new ConcurrentHashMap<>();
  private final Map<DictionaryEnvironmentType, ReadWriteLock> cacheLocks =
      new ConcurrentHashMap<>();
  private final Map<DictionaryEnvironmentType, Boolean> cacheInitialized =
      new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    log.info("Initializing category ranking cache...");
    // 모든 환경의 캐시 초기화
    for (DictionaryEnvironmentType envType : DictionaryEnvironmentType.values()) {
      environmentCaches.put(envType, new ConcurrentHashMap<>());
      cacheLocks.put(envType, new ReentrantReadWriteLock());
      cacheInitialized.put(envType, false);
    }
    // CURRENT 환경만 초기 로드
    loadCacheAsync(DictionaryEnvironmentType.CURRENT);
  }

  @Async("generalTaskExecutor")
  public void loadCacheAsync(DictionaryEnvironmentType environmentType) {
    ReadWriteLock lock = cacheLocks.get(environmentType);
    lock.writeLock().lock();
    try {
      loadCache(environmentType);
      cacheInitialized.put(environmentType, true);
      Map<String, List<CategoryWeight>> cache = environmentCaches.get(environmentType);
      log.info(
          "Category ranking cache initialized for {} with {} keywords",
          environmentType,
          cache.size());
    } finally {
      lock.writeLock().unlock();
    }
  }

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

    // 캐시가 초기화되지 않았으면 동기적으로 로드
    if (!Boolean.TRUE.equals(cacheInitialized.get(environmentType))) {
      log.debug(
          "Cache not initialized for {}, loading synchronously for query: {}",
          environmentType,
          query);
      loadCacheSync(environmentType);
    }

    ReadWriteLock lock = cacheLocks.get(environmentType);
    Map<String, List<CategoryWeight>> cache = environmentCaches.get(environmentType);

    lock.readLock().lock();
    try {
      Map<String, Integer> categoryWeights = new HashMap<>();
      Set<String> appliedKeywords = new HashSet<>(); // 이미 적용된 키워드 추적

      // 1. 먼저 공백 단위로 매칭
      String[] words = query.toLowerCase().split("\\s+");
      for (String word : words) {
        if (appliedKeywords.contains(word)) {
          continue; // 이미 적용된 키워드는 스킵
        }

        List<CategoryWeight> weights = cache.get(word);
        if (weights != null && !weights.isEmpty()) {
          for (CategoryWeight cw : weights) {
            categoryWeights.merge(cw.getCategory(), cw.getWeight(), Integer::sum);
          }
          appliedKeywords.add(word); // 적용된 키워드로 마킹
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
            continue; // 이미 적용된 키워드는 스킵
          }

          List<CategoryWeight> weights = cache.get(token);
          if (weights != null && !weights.isEmpty()) {
            for (CategoryWeight cw : weights) {
              categoryWeights.merge(cw.getCategory(), cw.getWeight(), Integer::sum);
            }
            appliedKeywords.add(token); // 적용된 키워드로 마킹
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
    } finally {
      lock.readLock().unlock();
    }
  }

  // 동기적 캐시 로드
  private void loadCacheSync(DictionaryEnvironmentType environmentType) {
    ReadWriteLock lock = cacheLocks.get(environmentType);
    lock.writeLock().lock();
    try {
      // 다시 확인 (double-check locking)
      if (!Boolean.TRUE.equals(cacheInitialized.get(environmentType))) {
        loadCache(environmentType);
        cacheInitialized.put(environmentType, true);
        Map<String, List<CategoryWeight>> cache = environmentCaches.get(environmentType);
        log.info(
            "Category ranking cache loaded synchronously for {} with {} keywords",
            environmentType,
            cache.size());
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void loadCache(DictionaryEnvironmentType environmentType) {
    try {
      log.info("카테고리 랭킹 사전 캐시 로딩 시작 - 환경: {}", environmentType);

      // 전체 리스트를 한 번에 가져와서 처리 (상세 API 호출 제거)
      var response = dictionaryService.getAllWithMappings(environmentType);

      Map<String, List<CategoryWeight>> cache = environmentCaches.get(environmentType);
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
      log.error("카테고리 랭킹 사전 캐시 로딩 실패 - 환경: {}", environmentType, e);
    }
  }

  public void updateCacheRealtime(DictionaryEnvironmentType environmentType) {
    if (environmentType == null) {
      environmentType = DictionaryEnvironmentType.CURRENT;
    }

    ReadWriteLock lock = cacheLocks.get(environmentType);
    lock.writeLock().lock();
    try {
      Map<String, List<CategoryWeight>> cache = environmentCaches.get(environmentType);
      cache.clear();
      loadCache(environmentType);
      cacheInitialized.put(environmentType, true);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public String getCacheStatus() {
    StringBuilder status = new StringBuilder();
    for (DictionaryEnvironmentType envType : DictionaryEnvironmentType.values()) {
      status.append(getCacheStatus(envType)).append("\n");
    }
    return status.toString();
  }

  public String getCacheStatus(DictionaryEnvironmentType environmentType) {
    ReadWriteLock lock = cacheLocks.get(environmentType);
    lock.readLock().lock();
    try {
      Map<String, List<CategoryWeight>> cache = environmentCaches.get(environmentType);
      int totalMappings = cache.values().stream().mapToInt(List::size).sum();
      return String.format(
          "Env: %s, Keywords: %d, Total mappings: %d, Initialized: %b",
          environmentType.name(),
          cache.size(),
          totalMappings,
          cacheInitialized.get(environmentType));
    } finally {
      lock.readLock().unlock();
    }
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
