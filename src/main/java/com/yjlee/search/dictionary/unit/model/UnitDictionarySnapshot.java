package com.yjlee.search.dictionary.unit.model;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import com.yjlee.search.dictionary.common.model.DictionarySnapshotEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "unit_dictionary_snapshots")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UnitDictionarySnapshot implements DictionarySnapshotEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  DictionaryEnvironmentType environmentType;

  @Column(nullable = false, length = 1000)
  String keyword;

  @CreatedDate @Column LocalDateTime createdAt;

  @LastModifiedDate @Column LocalDateTime updatedAt;

  public String getDescription() {
    return null;
  }

  public static UnitDictionarySnapshot createSnapshot(
      DictionaryEnvironmentType env, UnitDictionary entity) {
    return UnitDictionarySnapshot.builder()
        .environmentType(env)
        .keyword(entity.getKeyword())
        .build();
  }
}
