package com.yjlee.search.dictionary.stopword.model;

import com.yjlee.search.common.enums.EnvironmentType;
import com.yjlee.search.dictionary.common.model.DictionaryEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "stopword_dictionaries",
    indexes = {
      @Index(name = "idx_stopword_keyword_env", columnList = "keyword, environmentType"),
      @Index(name = "idx_stopword_env", columnList = "environmentType"),
      @Index(name = "idx_stopword_updated", columnList = "updatedAt DESC")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StopwordDictionary implements DictionaryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false, length = 1000)
  String keyword;

  @Column(length = 500)
  String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  EnvironmentType environmentType = EnvironmentType.CURRENT;

  @CreatedDate @Column LocalDateTime createdAt;

  @LastModifiedDate @Column LocalDateTime updatedAt;

  public void updateKeyword(String keyword) {
    this.keyword = keyword;
  }

  public void updateDescription(String description) {
    this.description = description;
  }

  public static StopwordDictionary of(
      String keyword, String description, EnvironmentType environment) {
    return StopwordDictionary.builder()
        .keyword(keyword)
        .description(description)
        .environmentType(environment)
        .build();
  }
}
