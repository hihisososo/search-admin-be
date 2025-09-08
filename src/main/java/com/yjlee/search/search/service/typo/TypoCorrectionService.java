package com.yjlee.search.search.service.typo;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.dictionary.typo.model.TypoCorrectionDictionary;
import com.yjlee.search.dictionary.typo.repository.TypoCorrectionDictionaryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypoCorrectionService {

  private final TypoCorrectionDictionaryRepository typoCorrectionDictionaryRepository;

  // 기본 환경(CURRENT)로 오타 교정
  public String applyTypoCorrection(String query) {
    return applyTypoCorrection(query, DictionaryEnvironmentType.CURRENT);
  }

  // 환경별 오타 교정
  public String applyTypoCorrection(String query, DictionaryEnvironmentType environmentType) {
    if (query == null || query.trim().isEmpty()) {
      return query;
    }

    Map<String, String> cache = loadTypoCorrectionCache(environmentType);
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

  @Cacheable(value = "typoCorrection", key = "#environmentType")
  public Map<String, String> loadTypoCorrectionCache(DictionaryEnvironmentType environmentType) {
    log.info("오타교정 사전 캐시 로딩 - 환경: {}", environmentType);

    List<TypoCorrectionDictionary> dictionaries =
        typoCorrectionDictionaryRepository.findByEnvironmentTypeOrderByKeywordAsc(environmentType);

    Map<String, String> cache = new HashMap<>();

    for (TypoCorrectionDictionary dict : dictionaries) {
      String key = TextPreprocessor.preprocess(dict.getKeyword());
      String value = TextPreprocessor.preprocess(dict.getCorrectedWord());
      if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
        cache.put(key, value);
      }
    }

    log.info("오타교정 사전 캐시 로딩 완료 - 환경: {}, 항목: {}개", environmentType, cache.size());
    return cache;
  }

  @CacheEvict(value = "typoCorrection", key = "#environmentType")
  public void updateCacheRealtime(DictionaryEnvironmentType environmentType) {
    log.info("오타교정 캐시 클리어 - 환경: {}", environmentType);
    // 캐시 제거만 하면 다음 요청 시 자동으로 재로드됨
  }

  @CacheEvict(value = "typoCorrection", allEntries = true)
  public void clearAllCache() {
    log.info("모든 오타교정 캐시 클리어");
  }
}
