package com.yjlee.search.dictionary.typo.model;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.model.DictionaryEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "typo_correction_dictionaries")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TypoCorrectionDictionary implements DictionaryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 100)
  String keyword; // 오타 단어 (예: "삼송")

  @Column(nullable = false, length = 100)
  String correctedWord; // 교정어 (예: "삼성")

  @Column(length = 500)
  String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  DictionaryEnvironmentType environmentType = DictionaryEnvironmentType.CURRENT;

  @CreatedDate @Column LocalDateTime createdAt;

  @LastModifiedDate @Column LocalDateTime updatedAt;

  public void updateKeyword(String keyword) {
    this.keyword = keyword;
  }

  public void updateCorrectedWord(String correctedWord) {
    this.correctedWord = correctedWord;
  }

  public void updateDescription(String description) {
    this.description = description;
  }
}
