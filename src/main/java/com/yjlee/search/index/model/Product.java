package com.yjlee.search.index.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 500, unique = true)
  String name;

  @Column(name = "thumbnail_url", length = 1000)
  String thumbnailUrl;

  Long price;

  @Column(columnDefinition = "TEXT")
  String specs;

  @Column(name = "reg_month", length = 20)
  String regMonth;

  @Column(precision = 3, scale = 1)
  BigDecimal rating;

  @Column(name = "review_count")
  Integer reviewCount;

  @Column(name = "category_id", nullable = false)
  Long categoryId;

  @Column(name = "category_name", nullable = false, length = 100)
  String categoryName;
}
