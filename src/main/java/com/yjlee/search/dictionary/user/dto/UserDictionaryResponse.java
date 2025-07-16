package com.yjlee.search.dictionary.user.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class UserDictionaryResponse {
  long id;
  String keyword;
  String description;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
}
