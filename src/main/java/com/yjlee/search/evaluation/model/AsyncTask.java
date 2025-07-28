package com.yjlee.search.evaluation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "async_tasks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AsyncTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  AsyncTaskType taskType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  AsyncTaskStatus status = AsyncTaskStatus.PENDING;

  @Column(nullable = false)
  @Builder.Default
  Integer progress = 0; // 0-100

  @Column(length = 1000)
  String message;

  @Column(columnDefinition = "TEXT")
  String errorMessage;

  @Column(columnDefinition = "TEXT")
  String result; // JSON 형태로 결과 저장

  @CreatedDate
  @Column(nullable = false, updatable = false)
  LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  LocalDateTime updatedAt;

  @Column LocalDateTime startedAt;

  @Column LocalDateTime completedAt;

  public void updateProgress(int progress, String message) {
    this.progress = progress;
    this.message = message;
    if (this.status == AsyncTaskStatus.PENDING) {
      this.status = AsyncTaskStatus.IN_PROGRESS;
      this.startedAt = LocalDateTime.now();
    }
  }

  public void complete(String result) {
    this.status = AsyncTaskStatus.COMPLETED;
    this.progress = 100;
    this.result = result;
    this.completedAt = LocalDateTime.now();
  }

  public void fail(String errorMessage) {
    this.status = AsyncTaskStatus.FAILED;
    this.errorMessage = errorMessage;
    this.completedAt = LocalDateTime.now();
  }
}
