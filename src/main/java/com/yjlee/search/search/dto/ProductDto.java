package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "상품 정보")
public class ProductDto {

  @Schema(description = "상품 ID", example = "1")
  private String id;

  @Schema(description = "Elasticsearch 스코어", example = "15.234567")
  private Double score;

  @Schema(description = "Elasticsearch explain 결과 (시뮬레이션에서 explain=true일 때만 포함)")
  private String explain;

  @Schema(description = "상품명", example = "아이폰 15 Pro")
  private String name;

  @Schema(description = "상품명 원본", example = "아이폰 15 Pro")
  private String nameRaw;

  @Schema(description = "모델명", example = "iPhone 15 Pro")
  private String model;

  @Schema(description = "브랜드", example = "Apple")
  private String brandName;

  @Schema(description = "카테고리", example = "스마트폰")
  private String categoryName;

  @Schema(description = "가격", example = "1200000")
  private Integer price;

  @Schema(description = "등록월", example = "2024-01")
  private String registeredMonth;

  @Schema(description = "평점", example = "4.5")
  private BigDecimal rating;

  @Schema(description = "리뷰 수", example = "234")
  private Integer reviewCount;

  @Schema(description = "썸네일 URL", example = "https://example.com/iphone15pro.jpg")
  private String thumbnailUrl;

  @Schema(description = "상품 스펙", example = "최신 A17 Pro 칩셋이 탑재된 프리미엄 스마트폰")
  private String specs;

  @Schema(description = "상품 스펙 원본", example = "최신 A17 Pro 칩셋이 탑재된 프리미엄 스마트폰")
  private String specsRaw;
}
