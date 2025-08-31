package com.yjlee.search.search.dto;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchConfig {

  @Builder.Default private Double vectorMinScore = null;

  @Builder.Default private Float nameVectorBoost = 0.7f;

  @Builder.Default private Float specsVectorBoost = 0.3f;

  @Builder.Default private Integer numCandidatesMultiplier = 3;

  @Builder.Default private Integer topK = 300;

  private List<Query> filterQueries;

  public int getNumCandidates() {
    return topK * numCandidatesMultiplier;
  }

  public static VectorSearchConfig defaultConfig() {
    return VectorSearchConfig.builder().build();
  }
}
