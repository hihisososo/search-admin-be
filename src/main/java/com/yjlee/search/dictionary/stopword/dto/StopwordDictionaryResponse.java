package com.yjlee.search.dictionary.stopword.dto;

import com.yjlee.search.dictionary.common.dto.BaseDictionaryResponse;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StopwordDictionaryResponse implements BaseDictionaryResponse {
  private Long id;
  private String keyword;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
