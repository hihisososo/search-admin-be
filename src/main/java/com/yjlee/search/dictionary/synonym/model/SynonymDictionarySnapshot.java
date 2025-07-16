package com.yjlee.search.dictionary.synonym.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "synonym_dictionary_snapshots")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SynonymDictionarySnapshot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false)
  String version;

  @Column(nullable = false)
  Long originalDictionaryId; // 원본 사전 ID

  // 배포 시점의 사전 데이터 스냅샷
  @Column(nullable = false, length = 1000)
  String keyword; // 배포 시점의 전체 키워드 정보

  @Column(length = 500)
  String description;

  @CreatedDate @Column LocalDateTime deployedAt;

  // 스냅샷 생성 편의 메서드
  public static SynonymDictionarySnapshot createSnapshot(
      String version, SynonymDictionary dictionary) {
    return SynonymDictionarySnapshot.builder()
        .version(version)
        .originalDictionaryId(dictionary.getId())
        .keyword(dictionary.getKeyword())
        .description(dictionary.getDescription())
        .build();
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
