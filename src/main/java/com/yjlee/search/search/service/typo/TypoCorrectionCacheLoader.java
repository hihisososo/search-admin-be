package com.yjlee.search.search.service.typo;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionaryRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypoCorrectionCacheLoader {

  private final TypoCorrectionDictionaryRepository typoCorrectionDictionaryRepository;

  @Cacheable(value = "typoCorrection", key = "#environmentType")
  public Map<String, String> loadCache(EnvironmentType environmentType) {
    log.info("오타교정 사전 캐시 로딩 - 환경: {}", environmentType);

    List<TypoCorrectionDictionary> dictionaries =
        typoCorrectionDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environmentType);

    Map<String, String> cache = new HashMap<>();
    for (TypoCorrectionDictionary dict : dictionaries) {
      if (dict.getKeyword() != null && dict.getCorrectedWord() != null) {
        cache.put(dict.getKeyword(), dict.getCorrectedWord());
      }
    }

    log.info("오타교정 사전 캐시 로딩 완료 - 환경: {}, 항목: {}개", environmentType, cache.size());
    return cache;
  }

  @CacheEvict(value = "typoCorrection", key = "#environmentType")
  public void evictCache(EnvironmentType environmentType) {
    log.info("오타교정 캐시 클리어 - 환경: {}", environmentType);
  }

  @CacheEvict(value = "typoCorrection", allEntries = true)
  public void evictAllCache() {
    log.info("오타교정 전체 캐시 클리어");
  }
}
