package com.yjlee.search.dictionary.stopword.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StopwordDictionaryListResponse {
  private Long id;
  private String keyword;
  private String description;
  private LocalDateTime updatedAt;
}
