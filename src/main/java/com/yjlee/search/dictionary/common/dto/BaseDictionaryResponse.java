package com.yjlee.search.dictionary.common.dto;

import java.time.LocalDateTime;

public interface BaseDictionaryResponse {
  Long getId();
  String getKeyword();
  String getDescription();
  LocalDateTime getCreatedAt();
  LocalDateTime getUpdatedAt();
}