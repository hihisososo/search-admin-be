package com.yjlee.search.dictionary.synonym.dto;

import com.yjlee.search.dictionary.synonym.model.SynonymDictionary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@Schema(description = "동의어 사전 목록 응답")
public class SynonymDictionaryListResponse {

  @Schema(description = "사전 ID", example = "1")
  long id;

  @Schema(description = "동의어 그룹 (콤마로 구분)", example = "TV,티브이,텔레비전")
  String keyword;

  @Schema(description = "생성 시간", example = "2024-01-01T10:00:00")
  LocalDateTime createdAt;

  @Schema(description = "수정 시간", example = "2024-01-01T10:00:00")
  LocalDateTime updatedAt;

  public static SynonymDictionaryListResponse from(SynonymDictionary entity) {
    return SynonymDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
