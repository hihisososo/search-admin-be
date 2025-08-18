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
}
