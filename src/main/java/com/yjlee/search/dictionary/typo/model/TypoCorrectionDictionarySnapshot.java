package com.yjlee.search.dictionary.typo.model;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "typo_correction_dictionary_snapshots")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TypoCorrectionDictionarySnapshot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  DictionaryEnvironmentType environmentType;

  @Column(nullable = false)
  Long originalDictionaryId; // 원본 사전 ID

  // 스냅샷 시점의 사전 데이터
  @Column(nullable = false, length = 100)
  String keyword; // 오타 단어

  @Column(nullable = false, length = 100)
  String correctedWord; // 교정어

  @Column(length = 500)
  String description;

  @CreatedDate @Column LocalDateTime createdAt;

  @LastModifiedDate @Column LocalDateTime updatedAt;

  // 스냅샷 생성 편의 메서드
  public static TypoCorrectionDictionarySnapshot createSnapshot(
      DictionaryEnvironmentType environmentType, TypoCorrectionDictionary dictionary) {
    return TypoCorrectionDictionarySnapshot.builder()
        .environmentType(environmentType)
        .originalDictionaryId(dictionary.getId())
        .keyword(dictionary.getKeyword())
        .correctedWord(dictionary.getCorrectedWord())
        .description(dictionary.getDescription())
        .build();
  }

  // 환경별 스냅샷 업데이트
  public void updateSnapshot(TypoCorrectionDictionary dictionary) {
    this.originalDictionaryId = dictionary.getId();
    this.keyword = dictionary.getKeyword();
    this.correctedWord = dictionary.getCorrectedWord();
    this.description = dictionary.getDescription();
  }
}
