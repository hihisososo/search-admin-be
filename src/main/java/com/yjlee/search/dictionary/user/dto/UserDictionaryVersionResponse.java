package com.yjlee.search.dictionary.user.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class UserDictionaryVersionResponse {
  long id;
  String version;
  int snapshotCount; // 해당 버전에 포함된 스냅샷 개수
  LocalDateTime createdAt;
}
