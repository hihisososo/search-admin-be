package com.yjlee.search.async.model;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "async_tasks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private AsyncTaskType taskType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private AsyncTaskStatus status = AsyncTaskStatus.PENDING;

  @Column(nullable = false)
  @Builder.Default
  private Integer progress = 0;

  @Column(length = 1000)
  private String message;

  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  @Column(columnDefinition = "TEXT")
  private String result;

  @Column(columnDefinition = "TEXT")
  private String params;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Column private LocalDateTime startedAt;

  @Column private LocalDateTime completedAt;

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
