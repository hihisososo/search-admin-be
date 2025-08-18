package com.yjlee.search.dictionary.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카테고리 랭킹 사전 목록 응답")
public class CategoryRankingDictionaryListResponse {

  @Schema(description = "ID", example = "1")
  private Long id;

  @Schema(description = "키워드", example = "아이폰")
  private String keyword;

  @Schema(description = "카테고리 개수", example = "3")
  private Integer categoryCount;

  @Schema(description = "설명", example = "아이폰 관련 카테고리 부스팅")
  private String description;

  @Schema(description = "수정일시", example = "2024-01-01T10:00:00")
  private LocalDateTime updatedAt;
}
