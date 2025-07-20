package com.yjlee.search.index.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.model.Product;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutocompleteDocument {

  String name;

  @JsonProperty("name_icu")
  String nameIcu;

  public static AutocompleteDocument from(Product product) {
    return AutocompleteDocument.builder()
        .name(product.getName())
        .nameIcu(TextPreprocessor.preprocess(product.getName()))
        .build();
  }
}
