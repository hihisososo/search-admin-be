package com.yjlee.search.deployment.domain;

import com.yjlee.search.deployment.model.IndexEnvironment;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeploymentContext {
  private final Long devEnvironmentId;
  private final Long prodEnvironmentId;
  private final String devIndexName;
  private final String devAutocompleteIndexName;
  private final String devVersion;
  private final Long devDocumentCount;

  private String previousProdIndexName;
  private String previousProdAutocompleteIndexName;

  private Long historyId;
  private boolean completed;

  public static DeploymentContext from(IndexEnvironment devEnv, IndexEnvironment prodEnv) {
    DeploymentContext context =
        DeploymentContext.builder()
            .devEnvironmentId(devEnv.getId())
            .prodEnvironmentId(prodEnv.getId())
            .devIndexName(devEnv.getIndexName())
            .devAutocompleteIndexName(devEnv.getAutocompleteIndexName())
            .devVersion(devEnv.getVersion())
            .devDocumentCount(devEnv.getDocumentCount())
            .build();

    if (prodEnv.getIndexName() != null) {
      context.setPreviousProdIndices(prodEnv.getIndexName(), prodEnv.getAutocompleteIndexName());
    }

    return context;
  }

  public void setHistoryId(Long historyId) {
    this.historyId = historyId;
  }

  public void setPreviousProdIndices(String indexName, String autocompleteIndexName) {
    this.previousProdIndexName = indexName;
    this.previousProdAutocompleteIndexName = autocompleteIndexName;
  }

  public void markCompleted() {
    this.completed = true;
  }
}
