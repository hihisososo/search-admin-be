package com.yjlee.search.dictionary.category.model;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.category.converter.CategoryMappingListConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "category_ranking_dictionary_snapshots",
    indexes = {
      @Index(name = "idx_category_snapshot_env", columnList = "environment_type"),
      @Index(name = "idx_category_snapshot_keyword", columnList = "keyword"),
      @Index(name = "idx_category_snapshot_env_keyword", columnList = "environment_type, keyword")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategoryRankingDictionarySnapshot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "environment_type", nullable = false)
  @Enumerated(EnumType.STRING)
  DictionaryEnvironmentType environmentType;

  @Column(nullable = false, length = 100)
  String keyword;

  @Column(name = "category_mappings", columnDefinition = "TEXT")
  @Convert(converter = CategoryMappingListConverter.class)
  @Builder.Default
  List<CategoryMapping> categoryMappings = new ArrayList<>();

  @Column(length = 500)
  String description;

  @CreatedDate @Column LocalDateTime createdAt;

  @LastModifiedDate @Column LocalDateTime updatedAt;

  public static CategoryRankingDictionarySnapshot createSnapshot(
      DictionaryEnvironmentType environmentType, CategoryRankingDictionary dictionary) {
    return CategoryRankingDictionarySnapshot.builder()
        .environmentType(environmentType)
        .keyword(dictionary.getKeyword())
        .categoryMappings(new ArrayList<>(dictionary.getCategoryMappings()))
        .description(dictionary.getDescription())
        .build();
  }
}
