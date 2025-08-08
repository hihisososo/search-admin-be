package com.yjlee.search.dictionary.stopword.recommendation.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stopword_recommendations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopwordRecommendation {

  @Id
  @Column(name = "term", nullable = false, length = 500)
  private String term;

  @Column(name = "recommendation_count")
  @Builder.Default
  private int recommendationCount = 0;

  @Column(name = "reason", length = 500)
  private String reason;
}


