package com.yjlee.search.dictionary.category.model;

import com.yjlee.search.dictionary.category.converter.CategoryMappingListConverter;
import com.yjlee.search.dictionary.common.model.DictionaryEntity;
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
@Table(name = "category_ranking_dictionaries")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategoryRankingDictionary implements DictionaryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, unique = true, length = 100)
  String keyword;

  @Column(name = "category_mappings", columnDefinition = "TEXT")
  @Convert(converter = CategoryMappingListConverter.class)
  @Builder.Default
  List<CategoryMapping> categoryMappings = new ArrayList<>();

  @Column(length = 500)
  String description;

  @CreatedDate @Column LocalDateTime createdAt;

  @LastModifiedDate @Column LocalDateTime updatedAt;

  public void updateKeyword(String keyword) {
    this.keyword = keyword;
  }

  public void updateCategoryMappings(List<CategoryMapping> categoryMappings) {
    this.categoryMappings = categoryMappings != null ? categoryMappings : new ArrayList<>();
  }

  public void updateDescription(String description) {
    this.description = description;
  }

  public void addCategoryMapping(String category, Integer weight) {
    if (categoryMappings == null) {
      categoryMappings = new ArrayList<>();
    }
    categoryMappings.add(new CategoryMapping(category, weight));
  }

  public void removeCategoryMapping(String category) {
    if (categoryMappings != null) {
      categoryMappings.removeIf(m -> m.getCategory().equals(category));
    }
  }
}
