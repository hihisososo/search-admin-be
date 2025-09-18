package com.yjlee.search.dictionary.unit.dto;

import com.yjlee.search.dictionary.unit.model.UnitDictionary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "단위 사전 목록 응답")
public class UnitDictionaryListResponse {

  @Schema(description = "사전 ID", example = "1")
  private Long id;

  @Schema(description = "단위 매핑", example = "kg,킬로그램")
  private String keyword;

  @Schema(description = "생성일시")
  private LocalDateTime createdAt;

  @Schema(description = "수정일시")
  private LocalDateTime updatedAt;

  public static UnitDictionaryListResponse from(UnitDictionary entity) {
    return UnitDictionaryListResponse.builder()
        .id(entity.getId())
        .keyword(entity.getKeyword())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
