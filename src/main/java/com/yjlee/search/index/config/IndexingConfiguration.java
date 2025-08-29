package com.yjlee.search.index.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "indexing")
@Getter
@Setter
public class IndexingConfiguration {

  private int batchSize = 500;
  private int maxConcurrentBatches = 4;
  private int corePoolSize = 4;
  private int maxPoolSize = 8;
  private int queueCapacity = 100;
  private long keepAliveTimeSeconds = 60;
  private long leakDetectionThresholdMs = 60000;
  private boolean enableProgressLogging = true;
  private boolean enableMetrics = false;

  public static class EmbeddingConfig {
    private int cacheSize = 10000;
    private boolean enableCache = true;
    private int embeddingPoolSize = 2;
    private int embeddingMaxPoolSize = 4;

    // Getters and setters
    public int getCacheSize() {
      return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
      this.cacheSize = cacheSize;
    }

    public boolean isEnableCache() {
      return enableCache;
    }

    public void setEnableCache(boolean enableCache) {
      this.enableCache = enableCache;
    }

    public int getEmbeddingPoolSize() {
      return embeddingPoolSize;
    }

    public void setEmbeddingPoolSize(int embeddingPoolSize) {
      this.embeddingPoolSize = embeddingPoolSize;
    }

    public int getEmbeddingMaxPoolSize() {
      return embeddingMaxPoolSize;
    }

    public void setEmbeddingMaxPoolSize(int embeddingMaxPoolSize) {
      this.embeddingMaxPoolSize = embeddingMaxPoolSize;
    }
  }

  private EmbeddingConfig embedding = new EmbeddingConfig();
}
