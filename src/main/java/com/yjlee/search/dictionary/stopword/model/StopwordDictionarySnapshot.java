package com.yjlee.search.dictionary.stopword.model;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "stopword_dictionary_snapshots")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StopwordDictionarySnapshot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  DictionaryEnvironmentType environmentType;

  @Column(nullable = false)
  Long originalDictionaryId; // 원본 사전 ID

  // 스냅샷 시점의 사전 데이터
  @Column(nullable = false, length = 1000)
  String keyword; // 스냅샷 시점의 전체 키워드 정보

  @Column(length = 500)
  String description;

  @CreatedDate @Column LocalDateTime createdAt;

  @LastModifiedDate @Column LocalDateTime updatedAt;

  // 스냅샷 생성 편의 메서드
  public static StopwordDictionarySnapshot createSnapshot(
      DictionaryEnvironmentType environmentType, StopwordDictionary dictionary) {
    return StopwordDictionarySnapshot.builder()
        .environmentType(environmentType)
        .originalDictionaryId(dictionary.getId())
        .keyword(dictionary.getKeyword())
        .description(dictionary.getDescription())
        .build();
  }

  // 환경별 스냅샷 업데이트
  public void updateSnapshot(StopwordDictionary dictionary) {
    this.originalDictionaryId = dictionary.getId();
    this.keyword = dictionary.getKeyword();
    this.description = dictionary.getDescription();
  }
}
