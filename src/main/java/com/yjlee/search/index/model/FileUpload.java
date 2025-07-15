package com.yjlee.search.index.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "file_uploads")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileUpload {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  String id;

  @Column(nullable = false)
  Long indexId;

  @Column(nullable = false)
  String originalFileName;

  @Column(nullable = false)
  String s3Key;

  @Column(nullable = false)
  String s3Url;

  @Column(nullable = false)
  Long fileSize;

  @Column(nullable = false)
  String contentType;

  @Column(nullable = false)
  @Builder.Default
  LocalDateTime uploadedAt = LocalDateTime.now();

  @Column(nullable = false)
  @Builder.Default
  String status = "UPLOADED"; // UPLOADED, PROCESSING, PROCESSED, FAILED
}
