package com.yjlee.search.search.service.typo;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.dictionary.typo.service.TypoCorrectionDictionaryService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypoCorrectionService {

  private final TypoCorrectionDictionaryService dictionaryService;
  private final Map<String, String> cache = new ConcurrentHashMap<>();
  private volatile DictionaryEnvironmentType activeEnvironmentType =
      DictionaryEnvironmentType.CURRENT;

  public String applyTypoCorrection(String query) {
    if (query == null || query.trim().isEmpty()) {
      return query;
    }

    if (cache.isEmpty()) {
      loadCache(activeEnvironmentType);
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
      log.error("Failed to load typo correction dictionary", e);
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
    return String.format("Env: %s, Cache size: %d", activeEnvironmentType.name(), cache.size());
  }
}
