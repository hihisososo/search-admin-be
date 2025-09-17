package com.yjlee.search.deployment.model;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.deployment.enums.IndexStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "index_environments")
@Getter
@Setter(AccessLevel.PACKAGE)
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

  @Column(name = "index_name")
  private String indexName;

  @Column(name = "autocomplete_index_name")
  private String autocompleteIndexName;

  @Column(name = "synonym_set_name")
  private String synonymSetName;

  @Column(name = "document_count")
  private Long documentCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "index_status", nullable = false)
  private IndexStatus indexStatus;

  @Column(name = "index_date")
  private LocalDateTime indexDate;

  @Column(name = "version")
  private String version;

  @CreationTimestamp
  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public static IndexEnvironment createNew(EnvironmentType type) {
    return IndexEnvironment.builder()
        .environmentType(type)
        .indexStatus(IndexStatus.INACTIVE)
        .documentCount(0L)
        .build();
  }

  public void activate(
      String indexName,
      String autocompleteIndexName,
      String synonymSetName,
      String version,
      Long documentCount) {
    this.indexStatus = IndexStatus.ACTIVE;
    this.indexName = indexName;
    this.autocompleteIndexName = autocompleteIndexName;
    this.synonymSetName = synonymSetName;
    this.version = version;
    this.documentCount = documentCount;
    this.indexDate = LocalDateTime.now();
  }

  public void deactivate() {
    this.indexStatus = IndexStatus.INACTIVE;
  }

  public void reset() {
    this.indexStatus = IndexStatus.INACTIVE;
    this.indexName = null;
    this.autocompleteIndexName = null;
    this.synonymSetName = null;
    this.documentCount = 0L;
    this.version = null;
    this.indexDate = null;
  }

  public void updatePrepareIndexing(
      String indexName, String autoCompleteIndexname, String synonymSetName, String version) {
    this.indexName = indexName;
    this.autocompleteIndexName = autoCompleteIndexname;
    this.synonymSetName = synonymSetName;
    this.version = version;
    this.indexStatus = IndexStatus.INACTIVE;
    this.documentCount = 0L;
  }

  public void switchFrom(IndexEnvironment source) {
    this.indexName = source.indexName;
    this.autocompleteIndexName = source.autocompleteIndexName;
    this.synonymSetName = source.synonymSetName;
    this.version = source.version;
    this.documentCount = source.documentCount;
    this.indexStatus = IndexStatus.ACTIVE;
    this.indexDate = LocalDateTime.now();
  }
}
