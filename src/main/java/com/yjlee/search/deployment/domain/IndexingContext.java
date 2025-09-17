package com.yjlee.search.deployment.domain;

import com.yjlee.search.dictionary.common.model.DictionaryData;
import com.yjlee.search.index.provider.IndexNameProvider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IndexingContext {
  private final String description;
  private final String version;
  private final String productIndexName;
  private final String autocompleteIndexName;
  private final String synonymSetName;
  private Long historyId;
  private int documentCount;
  private DictionaryData preloadedDictionaryData;

  public static IndexingContext create(
      Long historyId, String description, String version, IndexNameProvider indexNameProvider) {
    return IndexingContext.builder()
        .historyId(historyId)
        .description(description)
        .version(version)
        .productIndexName(indexNameProvider.getProductIndexName(version))
        .autocompleteIndexName(indexNameProvider.getAutocompleteIndexName(version))
        .synonymSetName(indexNameProvider.getSynonymSetName(version))
        .build();
  }

  public void setDocumentCount(int documentCount) {
    this.documentCount = documentCount;
  }

  public void setPreloadedDictionaryData(DictionaryData dictionaryData) {
    this.preloadedDictionaryData = dictionaryData;
  }
}
