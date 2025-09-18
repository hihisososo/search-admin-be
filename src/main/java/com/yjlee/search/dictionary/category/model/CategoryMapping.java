package com.yjlee.search.dictionary.category.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class CategoryMapping implements Serializable {
  private String category;

  @Builder.Default private Integer weight = 1000;

  public static CategoryMapping from(com.yjlee.search.dictionary.category.dto.CategoryMappingDto dto) {
    return CategoryMapping.builder()
        .category(dto.getCategory())
        .weight(dto.getWeight())
        .build();
  }

  public static java.util.List<CategoryMapping> fromDtos(java.util.List<com.yjlee.search.dictionary.category.dto.CategoryMappingDto> dtos) {
    if (dtos == null) {
      return new java.util.ArrayList<>();
    }
    return dtos.stream().map(CategoryMapping::from).toList();
  }
}
