package com.yjlee.search.dictionary.common.model;

import com.yjlee.search.common.enums.DictionaryEnvironmentType;
import java.time.LocalDateTime;

public interface DictionarySnapshotEntity {
  Long getId();

  String getKeyword();

  String getDescription();

  Long getOriginalDictionaryId();

  DictionaryEnvironmentType getEnvironmentType();

  LocalDateTime getCreatedAt();

  LocalDateTime getUpdatedAt();
}
