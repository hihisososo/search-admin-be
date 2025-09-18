package com.yjlee.search.dictionary.stopword.dto;

import com.yjlee.search.dictionary.stopword.model.StopwordDictionary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "불용어 사전 목록 응답")
public class StopwordDictionaryListResponse {

  @Schema(description = "사전 ID", example = "1")
  private Long id;

  @Schema(description = "불용어", example = "그리고")
  private String keyword;

  @Schema(description = "생성 시간", example = "2024-01-01T10:00:00")
  private LocalDateTime createdAt;

  @Schema(description = "수정 시간", example = "2024-01-01T10:00:00")
  private LocalDateTime updatedAt;

  public static StopwordDictionaryListResponse from(StopwordDictionary entity) {
    return StopwordDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
