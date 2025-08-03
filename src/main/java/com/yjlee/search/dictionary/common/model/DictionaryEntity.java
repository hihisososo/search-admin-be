package com.yjlee.search.dictionary.common.model;

import java.time.LocalDateTime;

public interface DictionaryEntity {
  Long getId();
  String getKeyword();
  String getDescription();
  LocalDateTime getCreatedAt();
  LocalDateTime getUpdatedAt();
  void updateKeyword(String keyword);
  void updateDescription(String description);
}