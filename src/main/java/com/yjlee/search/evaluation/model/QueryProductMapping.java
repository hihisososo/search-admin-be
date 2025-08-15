package com.yjlee.search.evaluation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "query_product_mappings")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QueryProductMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "evaluation_query_id", nullable = false)
  EvaluationQuery evaluationQuery;

  @Column(nullable = false)
  String productId;

  @Column(name = "product_name", columnDefinition = "TEXT")
  String productName;

  @Column(name = "product_specs", columnDefinition = "TEXT")
  String productSpecs;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  @Builder.Default
  RelevanceStatus relevanceStatus = RelevanceStatus.UNSPECIFIED;

  @Column(name = "relevance_score")
  @Builder.Default
  Integer relevanceScore = -1; // -1: 미평가, 0: 연관없음, 1: 스펙 일부 매치, 2: 제목 전부 매치

  @Column(columnDefinition = "TEXT")
  String evaluationReason;

  @Column(length = 20)
  @Builder.Default
  String evaluationSource = "USER";
}
