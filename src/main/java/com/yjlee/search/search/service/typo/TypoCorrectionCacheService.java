package com.yjlee.search.search.service.typo;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.model.IndexEnvironment;
import com.yjlee.search.deployment.service.IndexEnvironmentService;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionaryRepository;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypoCorrectionCacheService {

  private final IndexEnvironmentService indexEnvironmentService;
  private final TypoCorrectionDictionaryRepository typoCorrectionDictionaryRepository;

  private final Cache<String, Map<String, String>> versionedCache =
      Caffeine.newBuilder().maximumSize(5).build();

  @PostConstruct
  public void initializeCache() {
    log.info("오타교정 캐시 초기화 시작");
    for (EnvironmentType envType : EnvironmentType.values()) {
      IndexEnvironment env = indexEnvironmentService.getEnvironmentOrNull(envType);
      if (env != null && env.getVersion() != null) {
        String version = env.getVersion();
        Map<String, String> cache = loadFromDB(envType);
        versionedCache.put(version, cache);
        log.info("오타교정 캐시 로드 완료: env={}, version={}, keywords={}", envType, version, cache.size());
      }
    }
  }

  // 환경별 오타 교정
  public String applyTypoCorrection(String query, EnvironmentType environmentType) {
    if (query == null || query.trim().isEmpty()) {
      return query;
    }

    IndexEnvironment env = indexEnvironmentService.getEnvironment(environmentType);
    if (env == null || env.getVersion() == null) {
      log.warn("환경 정보 없음: environmentType={}", environmentType);
      return query;
    }

    Map<String, String> cache = versionedCache.getIfPresent(env.getVersion());
    if (cache == null) {
      log.warn("캐시 없음: version={}", env.getVersion());
      return query;
    }
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
  }

  public void addVersionCache(String version, Map<String, String> data) {
    versionedCache.put(version, data);
    log.info("오타교정 캐시 추가: version={}, keywords={}", version, data.size());
  }

  public void refreshCache(EnvironmentType environmentType) {
    IndexEnvironment env = indexEnvironmentService.getEnvironment(environmentType);
    if (env != null && env.getVersion() != null) {
      Map<String, String> cache = loadFromDB(environmentType);
      versionedCache.put(env.getVersion(), cache);
      log.info("오타교정 캐시 갱신: env={}, version={}", environmentType, env.getVersion());
    }
  }

  private Map<String, String> loadFromDB(EnvironmentType environmentType) {
    List<TypoCorrectionDictionary> dictionaries =
        typoCorrectionDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environmentType);

    Map<String, String> cache = new HashMap<>();
    for (TypoCorrectionDictionary dict : dictionaries) {
      if (dict.getKeyword() != null && dict.getCorrectedWord() != null) {
        cache.put(dict.getKeyword(), dict.getCorrectedWord());
      }
    }

    log.info("오타교정 DB 로드 완료 - 환경: {}, 항목: {}개", environmentType, cache.size());
    return cache;
  }

  public void realtimeSync(EnvironmentType environment) {
    log.info("오타교정 실시간 동기화 시작 - 환경: {}", environment);
    IndexEnvironment env = indexEnvironmentService.getEnvironment(environment);
    if (env != null && env.getVersion() != null) {
      versionedCache.invalidate(env.getVersion());
      refreshCache(environment);
    }
    log.info("오타교정 실시간 동기화 완료 - 환경: {}", environment);
  }

  public void syncWithPreloadedData(
      List<TypoCorrectionDictionary> typoCorrections, String version) {
    log.info("Preloaded 오타교정 동기화 시작 - 버전: {}", version);

    Map<String, String> cache = new HashMap<>();
    for (TypoCorrectionDictionary dict : typoCorrections) {
      if (dict.getKeyword() != null && dict.getCorrectedWord() != null) {
        cache.put(dict.getKeyword(), dict.getCorrectedWord());
      }
    }

    versionedCache.put(version, cache);
    log.info("Preloaded 오타교정 동기화 완료 - 버전: {}, 항목: {}개", version, cache.size());
  }
}
