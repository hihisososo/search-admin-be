package com.yjlee.search.index.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "product_embeddings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductEmbedding {
  
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;
  
  @Column(name = "product_id", nullable = false, unique = true)
  Long productId;
  
  @Column(name = "embedding_text", columnDefinition = "TEXT")
  String embeddingText;
  
  @Column(name = "embedding_vector", columnDefinition = "TEXT")
  String embeddingVector;
  
  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  LocalDateTime createdAt;
  
  @UpdateTimestamp
  @Column(name = "updated_at")
  LocalDateTime updatedAt;
}