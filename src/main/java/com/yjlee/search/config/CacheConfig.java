package com.yjlee.search.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager();
    cacheManager.setCaffeine(caffeineConfig());
    cacheManager.registerCustomCache(
        "categoryRanking",
        Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .recordStats()
            .build());
    cacheManager.registerCustomCache(
        "typoCorrection",
        Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .recordStats()
            .build());
    return cacheManager;
  }

  private Caffeine<Object, Object> caffeineConfig() {
    return Caffeine.newBuilder().maximumSize(500).expireAfterWrite(1, TimeUnit.HOURS).recordStats();
  }
}
