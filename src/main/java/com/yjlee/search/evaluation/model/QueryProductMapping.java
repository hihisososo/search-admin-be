package com.yjlee.search.evaluation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(
    name = "query_product_mappings",
    indexes = {@Index(name = "idx_evaluation_query_id", columnList = "evaluation_query_id")})
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

  @Column(name = "relevance_score")
  Integer relevanceScore; // null: 미평가, -1: 사람확인 필요, 0: 비연관, 1: 스펙 매치, 2: 제목 매치

  @Column(columnDefinition = "TEXT")
  String evaluationReason;

  @Column(length = 20)
  @Builder.Default
  String evaluationSource = "USER";

  @Column(name = "confidence")
  Double confidence;
}
