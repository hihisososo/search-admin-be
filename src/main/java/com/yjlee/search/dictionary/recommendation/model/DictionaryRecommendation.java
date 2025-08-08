package com.yjlee.search.dictionary.recommendation.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dictionary_recommendations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryRecommendation {

  @Id
  @Column(nullable = false, length = 100)
  private String word;

  @Column(name = "recommendation_count")
  @Builder.Default
  private int recommendationCount = 0;

  @Column(name = "reason", length = 500)
  private String reason;
}
