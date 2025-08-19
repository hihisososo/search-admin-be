package com.yjlee.search.dictionary.synonym.model;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "synonym_dictionary_snapshots")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SynonymDictionarySnapshot {
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
  public static SynonymDictionarySnapshot createSnapshot(
      DictionaryEnvironmentType environmentType, SynonymDictionary dictionary) {
    return SynonymDictionarySnapshot.builder()
        .environmentType(environmentType)
        .originalDictionaryId(dictionary.getId())
        .keyword(dictionary.getKeyword())
        .description(dictionary.getDescription())
        .build();
  }

  // 환경별 스냅샷 업데이트
  public void updateSnapshot(SynonymDictionary dictionary) {
    this.originalDictionaryId = dictionary.getId();
    this.keyword = dictionary.getKeyword();
    this.description = dictionary.getDescription();
  }

  // 기본 키워드 추출
  public String getBaseKeyword() {
    if (keyword == null || !keyword.contains(" => ")) {
      return keyword;
    }
    return keyword.split(" => ")[0];
  }

  // 유의어 목록 추출
  public String getSynonyms() {
    if (keyword == null || !keyword.contains(" => ")) {
      return "";
    }
    String[] parts = keyword.split(" => ");
    return parts.length > 1 ? parts[1] : "";
  }
}
