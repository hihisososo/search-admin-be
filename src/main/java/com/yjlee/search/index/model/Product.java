package com.yjlee.search.index.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 255)
  String name;

  @Column(length = 500)
  String thumbnailUrl;

  Long price;

  @Column(length = 20)
  String regMonth;

  @Builder.Default Long reviewCount = 0L;

  @Column(nullable = false)
  Long categoryId;

  @Column(nullable = false, length = 100)
  String categoryName;

  @Column(columnDefinition = "TEXT")
  String description;

  @CreatedDate
  @Column(name = "created_at")
  LocalDateTime createdAt;
}
