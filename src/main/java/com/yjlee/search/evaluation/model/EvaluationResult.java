package com.yjlee.search.evaluation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "evaluation_results")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EvaluationResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 500)
  String query;

  @Column(nullable = false)
  Double precision;

  @Column(nullable = false)
  Double recall;

  @Column(nullable = false)
  Integer correctCount;

  @Column(nullable = false)
  Integer totalSearchResults;

  @Column(nullable = false)
  Integer totalGroundTruth;
} 