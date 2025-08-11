package com.yjlee.search.dictionary.synonym.recommendation.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "synonym_term_recommendations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SynonymTermRecommendation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "base_term", nullable = false, length = 255)
  private String baseTerm;

  @Column(name = "synonym_term", nullable = false, length = 255)
  private String synonymTerm;

  @Column(name = "reason", length = 500)
  private String reason;

  @Builder.Default
  @Column(name = "recommendation_count", nullable = false)
  private int recommendationCount = 0;
}
