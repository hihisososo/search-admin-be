package com.yjlee.search.dictionary.synonym.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SynonymDictionaryListResponse {
  long id;
  String keyword;
  String description;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
}
