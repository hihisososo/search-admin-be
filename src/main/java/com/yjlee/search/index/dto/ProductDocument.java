package com.yjlee.search.index.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yjlee.search.common.util.TextPreprocessor;
import com.yjlee.search.index.model.Product;
import com.yjlee.search.index.util.BrandExtractor;
import com.yjlee.search.index.util.ModelExtractor;
import com.yjlee.search.index.util.RegDateExtractor;
import java.math.BigDecimal;
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

  @JsonProperty("brand_name")
  String brandName;

  @JsonProperty("thumbnail_url")
  String thumbnailUrl;

  Integer price;

  @JsonProperty("reg_month")
  String registeredMonth;

  BigDecimal rating;

  @JsonProperty("review_count")
  Integer reviewCount;

  @JsonProperty("category_name")
  String categoryName;

  String specs;

  @JsonProperty("specs_raw")
  String specsRaw;

  @JsonProperty("name_specs_vector")
  List<Float> nameSpecsVector;

  public static ProductDocument from(Product product) {
    return ProductDocument.builder()
        .id(String.valueOf(product.getId()))
        .name(TextPreprocessor.preprocess(product.getName()))
        .nameRaw(product.getName())
        .model(ModelExtractor.extractModels(product.getName()))
        .brandName(BrandExtractor.extractBrand(product.getName()))
        .thumbnailUrl(product.getThumbnailUrl())
        .price(product.getPrice() != null ? product.getPrice().intValue() : null)
        .registeredMonth(RegDateExtractor.extractRegDate(product.getRegMonth()))
        .rating(product.getRating())
        .reviewCount(product.getReviewCount() != null ? product.getReviewCount() : 0)
        .categoryName(product.getCategoryName())
        .specs(TextPreprocessor.preprocess(product.getSpecs() + " " + product.getCategoryName()))
        .specsRaw(product.getSpecs())
        .build();
  }
}
