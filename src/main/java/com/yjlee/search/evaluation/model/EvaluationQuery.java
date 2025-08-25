package com.yjlee.search.evaluation.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "evaluation_queries")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EvaluationQuery {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, unique = true, length = 500)
  String query;

  @Column(name = "expanded_tokens", columnDefinition = "TEXT")
  String expandedTokens;

  @Column(name = "expanded_synonyms_map", columnDefinition = "TEXT")
  String expandedSynonymsMap;

  @OneToMany(
      mappedBy = "evaluationQuery",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  List<QueryProductMapping> queryProductMappings = new ArrayList<>();

  @CreatedDate
  @Column(nullable = false, updatable = false)
  LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  LocalDateTime updatedAt;
}
