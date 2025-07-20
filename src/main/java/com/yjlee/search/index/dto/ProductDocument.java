package com.yjlee.search.index.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.util.BrandExtractor;
import com.yjlee.search.index.util.ModelExtractor;
import java.util.List;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductDocument {

  String id;
  String name;

  @JsonProperty("name_raw")
  String nameRaw;

  List<String> model;
  String brand;

  @JsonProperty("thumbnail_url")
  String thumbnailUrl;

  Integer price;

  @JsonProperty("registered_month")
  String registeredMonth;

  @JsonProperty("review_count")
  Integer reviewCount;

  String category;
  String description;

  @JsonProperty("description_raw")
  String descriptionRaw;

  public static ProductDocument from(Product product) {
    return ProductDocument.builder()
        .id(String.valueOf(product.getId()))
        .name(TextPreprocessor.preprocess(product.getName()))
        .nameRaw(product.getName())
        .model(ModelExtractor.extractModels(product.getName()))
        .brand(BrandExtractor.extractBrand(product.getName()))
        .thumbnailUrl(product.getThumbnailUrl())
        .price(product.getPrice() != null ? product.getPrice().intValue() : null)
        .registeredMonth(product.getRegMonth().replace(".", "-"))
        .reviewCount(product.getReviewCount() != null ? product.getReviewCount().intValue() : 0)
        .category(product.getCategoryName())
        .description(TextPreprocessor.preprocess(product.getDescription()))
        .descriptionRaw(product.getDescription())
        .build();
  }
}
