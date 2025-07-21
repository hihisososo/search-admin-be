package com.yjlee.search.dictionary.synonym.model;

import com.yjlee.search.common.enums.DictionaryDeploymentStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "synonym_dictionary_versions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SynonymDictionaryVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, unique = true)
  String version;

  @Column(length = 500)
  String description;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  DictionaryDeploymentStatus status = DictionaryDeploymentStatus.PENDING;

  @Column String deployPath;

  @Column String fileName;

  @Column Long fileSize;

  @Column LocalDateTime deployedAt;

  @CreatedDate @Column LocalDateTime createdAt;

  public void updateStatus(DictionaryDeploymentStatus status) {
    this.status = status;
  }

  public void markAsDeployed(String deployPath, String fileName, Long fileSize) {
    this.status = DictionaryDeploymentStatus.DEPLOYED;
    this.deployPath = deployPath;
    this.fileName = fileName;
    this.fileSize = fileSize;
    this.deployedAt = LocalDateTime.now();
  }

  public void markAsDeploying() {
    this.status = DictionaryDeploymentStatus.DEPLOYING;
  }

  public void markAsFailed() {
    this.status = DictionaryDeploymentStatus.FAILED;
  }

  public boolean canDeploy() {
    return status.canDeploy();
  }

  public boolean canDelete() {
    return status.canDelete();
  }
}
