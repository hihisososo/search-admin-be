package com.yjlee.search.searchsimulation.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "search_queries")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchSimulationQuery {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 200)
  String name;

  @Column(length = 500)
  String description;

  @Column(nullable = false, columnDefinition = "TEXT")
  String queryDsl;

  @Column(nullable = false, length = 100)
  String indexName;

  @CreatedDate @Column LocalDateTime createdAt;

  @LastModifiedDate @Column LocalDateTime updatedAt;

  // 비즈니스 메서드들
  public void updateName(String name) {
    this.name = name;
  }

  public void updateDescription(String description) {
    this.description = description;
  }

  public void updateQueryDsl(String queryDsl) {
    this.queryDsl = queryDsl;
  }

  public void updateIndexName(String indexName) {
    this.indexName = indexName;
  }
}
