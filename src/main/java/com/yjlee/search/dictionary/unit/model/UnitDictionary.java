package com.yjlee.search.dictionary.unit.model;

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
    name = "unit_dictionaries",
    indexes = {
      @Index(name = "idx_unit_keyword_env", columnList = "keyword, environmentType"),
      @Index(name = "idx_unit_env", columnList = "environmentType"),
      @Index(name = "idx_unit_updated", columnList = "updatedAt DESC")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UnitDictionary {
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

  public void updateKeyword(String keyword) {
    this.keyword = keyword;
  }

  public static UnitDictionary of(String keyword, EnvironmentType environment) {
    return UnitDictionary.builder().keyword(keyword).environmentType(environment).build();
  }

  public static UnitDictionary copyWithEnvironment(
      UnitDictionary source, EnvironmentType targetEnvironment) {
    return UnitDictionary.builder()
        .keyword(source.keyword)
        .environmentType(targetEnvironment)
        .build();
  }
}
