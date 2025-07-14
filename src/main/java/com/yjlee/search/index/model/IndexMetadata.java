package com.yjlee.search.index.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "index_meta_data")
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IndexMetadata {
  @Id String id;

  @Column(nullable = false, unique = true)
  String name;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  String dataSource;

  @Column(length = 1000)
  String jdbcUrl;

  @Column(length = 100)
  String jdbcUser;

  @Column(length = 255)
  String jdbcPassword;

  String jdbcQuery;

  @Column ZonedDateTime lastIndexedAt;

  @Column(nullable = false)
  @Builder.Default
  LocalDateTime createdAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  LocalDateTime updatedAt = LocalDateTime.now();

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
