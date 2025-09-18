package com.yjlee.search.dictionary.typo.model;

import com.yjlee.search.common.enums.EnvironmentType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "typo_correction_dictionaries",
    indexes = {
      @Index(name = "idx_typo_keyword_env", columnList = "keyword, environmentType"),
      @Index(name = "idx_typo_env", columnList = "environmentType"),
      @Index(name = "idx_typo_updated", columnList = "updatedAt DESC")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TypoCorrectionDictionary {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 100)
  String keyword;

  @Column(nullable = false, length = 100)
  String correctedWord;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  EnvironmentType environmentType = EnvironmentType.CURRENT;

  @CreatedDate @Column LocalDateTime createdAt;

  @LastModifiedDate @Column LocalDateTime updatedAt;

  public void updateKeyword(String keyword) {
    this.keyword = keyword;
  }

  public void updateCorrectedWord(String correctedWord) {
    this.correctedWord = correctedWord;
  }

  public static TypoCorrectionDictionary of(
      String keyword, String correctedWord, EnvironmentType environment) {
    return TypoCorrectionDictionary.builder()
        .keyword(keyword)
        .correctedWord(correctedWord)
        .environmentType(environment)
        .build();
  }

  public static TypoCorrectionDictionary copyWithEnvironment(
      TypoCorrectionDictionary source, EnvironmentType targetEnvironment) {
    return TypoCorrectionDictionary.builder()
        .keyword(source.keyword)
        .correctedWord(source.correctedWord)
        .environmentType(targetEnvironment)
        .build();
  }
}
