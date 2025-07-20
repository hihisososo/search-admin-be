package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "상품 정보")
public class ProductDto {

  @Schema(description = "상품 ID", example = "1")
  private String id;

  @Schema(description = "상품명", example = "아이폰 15 Pro")
  private String name;

  @Schema(description = "상품명 원본", example = "아이폰 15 Pro")
  private String nameRaw;

  @Schema(description = "모델명", example = "iPhone 15 Pro")
  private List<String> model;

  @Schema(description = "브랜드", example = "Apple")
  private String brand;

  @Schema(description = "카테고리", example = "스마트폰")
  private String category;

  @Schema(description = "가격", example = "1200000")
  private Integer price;

  @Schema(description = "등록월", example = "2024-01")
  private String registeredMonth;

  @Schema(description = "리뷰 수", example = "234")
  private Long reviewCount;

  @Schema(description = "썸네일 URL", example = "https://example.com/iphone15pro.jpg")
  private String thumbnailUrl;

  @Schema(description = "상품 설명", example = "최신 A17 Pro 칩셋이 탑재된 프리미엄 스마트폰")
  private String description;

  @Schema(description = "상품 설명 원본", example = "최신 A17 Pro 칩셋이 탑재된 프리미엄 스마트폰")
  private String descriptionRaw;
}
