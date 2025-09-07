package com.yjlee.search.index.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "text_embeddings",
    indexes = {@Index(name = "idx_text_embeddings_hash", columnList = "hash", unique = true)})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TextEmbedding {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(columnDefinition = "TEXT", nullable = false)
  String text;

  @Column(name = "hash", length = 64, nullable = false, unique = true)
  String hash;

  @Column(name = "vector", columnDefinition = "TEXT", nullable = false)
  String vector;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  LocalDateTime createdAt;
}
