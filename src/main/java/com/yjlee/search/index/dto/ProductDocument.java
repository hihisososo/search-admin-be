package com.yjlee.search.index.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
}
