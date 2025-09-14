package com.yjlee.search.deployment.model;

import com.yjlee.search.common.enums.EnvironmentType;
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

  public enum IndexStatus {
    ACTIVE("활성"),
    INACTIVE("비활성");

    private final String description;

    IndexStatus(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  public void activate(String version, Long documentCount) {
    this.indexStatus = IndexStatus.ACTIVE;
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
  }
}
