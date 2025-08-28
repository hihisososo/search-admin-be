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

  String model;

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

  String category;

  String specs;

  @JsonProperty("specs_raw")
  String specsRaw;

  @JsonProperty("name_vector")
  List<Float> nameVector;

  @JsonProperty("specs_vector")
  List<Float> specsVector;

  String units;

  @JsonProperty("name_candidate")
  String nameCandidate;

  @JsonProperty("specs_candidate")
  String specsCandidate;

  @JsonProperty("category_candidate")
  String categoryCandidate;
}
