package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "정렬 옵션")
public class ProductSortDto {

  @NotBlank(message = "정렬 필드는 필수입니다")
  @Pattern(
      regexp = "^(score|price|rating|reviewCount|registeredMonth)$",
      message = "정렬 필드는 score, price, rating, reviewCount, registeredMonth 중 하나여야 합니다")
  @Schema(
      description = "정렬 필드",
      example = "score",
      allowableValues = {"score", "price", "rating", "reviewCount", "registeredMonth"})
  private String field;

  @NotBlank(message = "정렬 순서는 필수입니다")
  @Pattern(regexp = "^(asc|desc)$", message = "정렬 순서는 asc 또는 desc여야 합니다")
  @Schema(
      description = "정렬 순서",
      example = "desc",
      allowableValues = {"asc", "desc"})
  private String order;

  public ProductSortType getSortType() {
    return ProductSortType.fromValue(field);
  }

  public ProductSortOrder getSortOrder() {
    return ProductSortOrder.fromValue(order);
  }
}
