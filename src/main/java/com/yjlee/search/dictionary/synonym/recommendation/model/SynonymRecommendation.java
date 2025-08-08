package com.yjlee.search.dictionary.synonym.recommendation.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "synonym_recommendations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SynonymRecommendation {

  @Id
  @Column(name = "synonym_group", nullable = false, length = 1000)
  private String synonymGroup; // 예: "랩탑,laptop,노트북" (정렬/정규화된 콤마 문자열)

  @Column(name = "recommendation_count")
  @Builder.Default
  private int recommendationCount = 0;

  @Column(name = "reason", length = 500)
  private String reason;
}


