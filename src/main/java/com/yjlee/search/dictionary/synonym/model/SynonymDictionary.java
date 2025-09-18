package com.yjlee.search.dictionary.synonym.model;

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
    name = "synonym_dictionaries",
    indexes = {
      @Index(name = "idx_synonym_keyword_env", columnList = "keyword, environmentType"),
      @Index(name = "idx_synonym_env", columnList = "environmentType"),
      @Index(name = "idx_synonym_updated", columnList = "updatedAt DESC")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SynonymDictionary {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 1000)
  String keyword;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  EnvironmentType environmentType = EnvironmentType.CURRENT;

  @CreatedDate @Column LocalDateTime createdAt;

  @LastModifiedDate @Column LocalDateTime updatedAt;

  // 비즈니스 메서드들
  public void updateKeyword(String keyword) {
    this.keyword = keyword;
  }

  public static SynonymDictionary of(String keyword, EnvironmentType environment) {
    return SynonymDictionary.builder()
        .keyword(keyword)
        .environmentType(environment)
        .build();
  }


  public static SynonymDictionary copyWithEnvironment(SynonymDictionary source, EnvironmentType targetEnvironment) {
    return SynonymDictionary.builder()
        .keyword(source.keyword)
        .environmentType(targetEnvironment)
        .build();
  }
}
