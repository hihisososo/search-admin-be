package com.yjlee.search.dictionary.typo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Schema(description = "오타교정 사전 응답")
public class TypoCorrectionDictionaryResponse {

  @Schema(description = "ID", example = "1")
  private Long id;

  @Schema(description = "오타 단어", example = "삼송")
  private String keyword;

  @Schema(description = "교정어", example = "삼성")
  private String correctedWord;

  @Schema(description = "설명", example = "삼성 브랜드 오타")
  private String description;

  @Schema(description = "생성일시", example = "2024-01-01T00:00:00Z")
  private LocalDateTime createdAt;

  @Schema(description = "수정일시", example = "2024-01-01T00:00:00Z")
  private LocalDateTime updatedAt;
}
