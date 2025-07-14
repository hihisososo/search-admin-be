package com.yjlee.search.index.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "file_uploads")
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileUpload {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  String id;

  @Column(nullable = false)
  String indexId; // 어떤 색인에 속하는 파일인지

  @Column(nullable = false)
  String originalFileName;

  @Column(nullable = false)
  String s3Key; // S3에 저장된 키

  @Column(nullable = false)
  String s3Url; // S3 접근 URL

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
