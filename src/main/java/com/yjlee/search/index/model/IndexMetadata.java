package com.yjlee.search.index.model;

import com.yjlee.search.common.converter.JsonbConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Map;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "index_meta_data")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IndexMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, unique = true)
  String name;

  @Column(length = 500)
  String description;

  @Column(nullable = false)
  String status;

  @Column(length = 200)
  String fileName;

  @Convert(converter = JsonbConverter.class)
  private Map<String, Object> mappings;

  @Convert(converter = JsonbConverter.class)
  private Map<String, Object> settings;

  @Column ZonedDateTime lastIndexedAt;
  @CreatedDate @Column LocalDateTime createdAt;
  @LastModifiedDate @Column LocalDateTime updatedAt;

  public void updateDescription(String description) {
    this.description = description;
  }

  public void updateStatus(String status) {
    this.status = status;
  }

  public void updateLastIndexedAt(ZonedDateTime lastIndexedAt) {
    this.lastIndexedAt = lastIndexedAt;
  }
}
