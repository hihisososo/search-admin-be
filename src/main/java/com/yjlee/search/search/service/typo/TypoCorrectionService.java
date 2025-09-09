package com.yjlee.search.search.service.typo;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TypoCorrectionService {

  private final TypoCorrectionCacheLoader cacheLoader;

  // 기본 환경(CURRENT)로 오타 교정
  public String applyTypoCorrection(String query) {
    return applyTypoCorrection(query, DictionaryEnvironmentType.CURRENT);
  }

  // 환경별 오타 교정
  public String applyTypoCorrection(String query, DictionaryEnvironmentType environmentType) {
    if (query == null || query.trim().isEmpty()) {
      return query;
    }

    Map<String, String> cache = cacheLoader.loadCache(environmentType);
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

  public void updateCacheRealtime(DictionaryEnvironmentType environmentType) {
    cacheLoader.evictCache(environmentType);
  }

  public void clearAllCache() {
    cacheLoader.evictAllCache();
  }
}
