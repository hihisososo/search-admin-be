package com.yjlee.search.index.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductIndexingResponse {

  boolean success;
  String message;
  Integer totalIndexed;
  String indexName;

  public static ProductIndexingResponse success(String message) {
    return ProductIndexingResponse.builder().success(true).message(message).build();
  }

  public static ProductIndexingResponse success(String message, Integer totalIndexed) {
    return ProductIndexingResponse.builder()
        .success(true)
        .message(message)
        .totalIndexed(totalIndexed)
        .indexName("products")
        .build();
  }

  public static ProductIndexingResponse error(String message) {
    return ProductIndexingResponse.builder().success(false).message(message).build();
  }
}
