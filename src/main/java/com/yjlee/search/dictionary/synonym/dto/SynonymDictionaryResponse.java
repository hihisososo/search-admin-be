package com.yjlee.search.dictionary.synonym.dto;

import com.yjlee.search.dictionary.common.dto.BaseDictionaryResponse;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class SynonymDictionaryResponse implements BaseDictionaryResponse {
  Long id;
  String keyword;
  String description;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
}
