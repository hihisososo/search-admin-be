package com.yjlee.search.deployment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "index_environments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexEnvironment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "environment_type", nullable = false, unique = true)
  private EnvironmentType environmentType;

  @Column(name = "index_name", nullable = false)
  private String indexName;

  @Column(name = "document_count")
  private Long documentCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "index_status", nullable = false)
  private IndexStatus indexStatus;

  @Column(name = "index_date")
  private LocalDateTime indexDate;

  @Column(name = "version")
  private String version;

  @Column(name = "is_indexing", nullable = false)
  private Boolean isIndexing;

  @Column(name = "indexed_document_count")
  private Long indexedDocumentCount;

  @Column(name = "total_document_count")
  private Long totalDocumentCount;

  @CreationTimestamp
  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public enum EnvironmentType {
    DEV("개발"),
    PROD("운영");

    private final String description;

    EnvironmentType(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  public enum IndexStatus {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    INDEXING("색인중"),
    FAILED("실패");

    private final String description;

    IndexStatus(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  public void startIndexing() {
    this.isIndexing = true;
    this.indexStatus = IndexStatus.INDEXING;
    this.indexedDocumentCount = 0L;
    this.totalDocumentCount = null;
  }

  public void completeIndexing(String version, Long documentCount) {
    this.isIndexing = false;
    this.indexStatus = IndexStatus.ACTIVE;
    this.version = version;
    this.documentCount = documentCount;
    this.indexDate = LocalDateTime.now();
    this.indexedDocumentCount = documentCount;
    this.totalDocumentCount = documentCount;
  }

  public void failIndexing() {
    this.isIndexing = false;
    this.indexStatus = IndexStatus.FAILED;
  }

  public void updateIndexingProgress(Long indexedCount, Long totalCount) {
    this.indexedDocumentCount = indexedCount;
    this.totalDocumentCount = totalCount;
  }

  public Integer getIndexingProgress() {
    if (totalDocumentCount == null || totalDocumentCount == 0) {
      return 0;
    }
    if (indexedDocumentCount == null) {
      return 0;
    }
    return (int) ((indexedDocumentCount * 100) / totalDocumentCount);
  }
}
