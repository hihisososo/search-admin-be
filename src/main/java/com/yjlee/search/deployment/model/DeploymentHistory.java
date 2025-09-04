package com.yjlee.search.deployment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "deployment_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "deployment_type", nullable = false)
  private DeploymentType deploymentType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private DeploymentStatus status;

  @Column(name = "version", nullable = false)
  private String version;

  @Column(name = "document_count")
  private Long documentCount;

  @Column(name = "description")
  private String description;

  @Column(name = "deployment_time")
  private LocalDateTime deploymentTime;

  @CreationTimestamp
  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public enum DeploymentType {
    INDEXING("색인"),
    DEPLOYMENT("배포"),
    CLEANUP("정리");

    private final String description;

    DeploymentType(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  public enum DeploymentStatus {
    SUCCESS("성공"),
    FAILED("실패"),
    IN_PROGRESS("진행중"),
    COMPLETED("완료"),
    PARTIAL("부분완료");

    private final String description;

    DeploymentStatus(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  public void complete(LocalDateTime deploymentTime, Long documentCount) {
    this.status = DeploymentStatus.SUCCESS;
    this.deploymentTime = deploymentTime;
    this.documentCount = documentCount;
  }

  public void fail() {
    this.status = DeploymentStatus.FAILED;
    this.deploymentTime = LocalDateTime.now();
  }
}
