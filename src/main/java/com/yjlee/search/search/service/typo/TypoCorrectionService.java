package com.yjlee.search.search.service.typo;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
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

  public String applyTypoCorrection(String query) {
    if (query == null || query.trim().isEmpty()) {
      return query;
    }

    if (cache.isEmpty()) {
      loadCache();
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

  private void loadCache() {
    try {
      var response =
          dictionaryService.getTypoCorrectionDictionaries(1, 1000, null, "keyword", "asc", null);

      response.getContent().forEach(dict -> cache.put(dict.getKeyword(), dict.getCorrectedWord()));

    } catch (Exception e) {
      log.error("Failed to load typo correction dictionary", e);
    }
  }

  public void updateCacheRealtime(DictionaryEnvironmentType environmentType) {
    cache.clear();
    loadCache();
  }

  public String getCacheStatus() {
    return String.format("Cache size: %d", cache.size());
  }
}
