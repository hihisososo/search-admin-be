package com.yjlee.search.dictionary.typo.recommendation.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "typo_correction_recommendations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypoCorrectionRecommendation {

  @Id
  @Column(name = "pair", nullable = false, length = 500)
  private String pair; // 예: "무선 키보드,무선키보드" 공백 기준 교정 페어

  @Column(name = "recommendation_count")
  @Builder.Default
  private int recommendationCount = 0;

  @Column(name = "reason", length = 500)
  private String reason;
}
