package com.yjlee.search.search.service.typo;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import jakarta.annotation.PostConstruct;
import java.util.Map;
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
public class TypoCorrectionService {

  private final TypoCorrectionDictionaryService dictionaryService;

  // 환경별 캐시 맵
  private final Map<DictionaryEnvironmentType, Map<String, String>> environmentCaches =
      new ConcurrentHashMap<>();
  private final Map<DictionaryEnvironmentType, ReadWriteLock> cacheLocks =
      new ConcurrentHashMap<>();
  private final Map<DictionaryEnvironmentType, Boolean> cacheInitialized =
      new ConcurrentHashMap<>();

  @PostConstruct
  public void initCache() {
    log.info("Initializing typo correction cache...");
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
      Map<String, String> cache = environmentCaches.get(environmentType);
      log.info(
          "Typo correction cache initialized for {} with {} entries",
          environmentType,
          cache.size());
    } finally {
      lock.writeLock().unlock();
    }
  }

  // 기본 환경(CURRENT)로 오타 교정
  public String applyTypoCorrection(String query) {
    return applyTypoCorrection(query, DictionaryEnvironmentType.CURRENT);
  }

  // 환경별 오타 교정
  public String applyTypoCorrection(String query, DictionaryEnvironmentType environmentType) {
    if (query == null || query.trim().isEmpty()) {
      return query;
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
    Map<String, String> cache = environmentCaches.get(environmentType);

    lock.readLock().lock();
    try {
      String[] words = query.split("\\s+");
      StringBuilder result = new StringBuilder();

      for (String word : words) {
        if (result.length() > 0) {
          result.append(" ");
        }
        String corrected = cache.get(word);
        result.append(corrected != null ? corrected : word);
      }

      return result.toString();
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
        Map<String, String> cache = environmentCaches.get(environmentType);
        log.info(
            "Typo correction cache loaded synchronously for {} with {} entries",
            environmentType,
            cache.size());
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void loadCache(DictionaryEnvironmentType environmentType) {
    try {
      var response =
          dictionaryService.getTypoCorrectionDictionaries(
              0, // 첫 페이지부터 로딩
              10000, // 충분히 큰 페이지 크기(사전 규모에 맞춰 조정 가능)
              null,
              "keyword",
              "asc",
              environmentType);

      Map<String, String> cache = environmentCaches.get(environmentType);
      cache.clear();

      response
          .getContent()
          .forEach(
              dict -> {
                String key = TextPreprocessor.preprocess(dict.getKeyword());
                String value = TextPreprocessor.preprocess(dict.getCorrectedWord());
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                  cache.put(key, value);
                }
              });

    } catch (Exception e) {
      log.error(
          "Failed to load typo correction dictionary for environment: {}", environmentType, e);
    }
  }

  public void updateCacheRealtime(DictionaryEnvironmentType environmentType) {
    if (environmentType == null) {
      environmentType = DictionaryEnvironmentType.CURRENT;
    }

    ReadWriteLock lock = cacheLocks.get(environmentType);
    lock.writeLock().lock();
    try {
      Map<String, String> cache = environmentCaches.get(environmentType);
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
      Map<String, String> cache = environmentCaches.get(environmentType);
      return String.format(
          "Env: %s, Cache size: %d, Initialized: %b",
          environmentType.name(), cache.size(), cacheInitialized.get(environmentType));
    } finally {
      lock.readLock().unlock();
    }
  }
}
