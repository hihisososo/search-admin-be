package com.yjlee.search.dictionary.category.dto;

import com.yjlee.search.dictionary.category.model.CategoryMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카테고리 랭킹 사전 응답")
public class CategoryRankingDictionaryResponse {

  @Schema(description = "ID", example = "1")
  private Long id;

  @Schema(description = "키워드", example = "아이폰")
  private String keyword;

  @Schema(description = "카테고리 매핑 목록")
  private List<CategoryMappingDto> categoryMappings;

  @Schema(description = "설명", example = "아이폰 관련 카테고리 부스팅")
  private String description;

  @Schema(description = "생성일시", example = "2024-01-01T10:00:00")
  private LocalDateTime createdAt;

  @Schema(description = "수정일시", example = "2024-01-01T10:00:00")
  private LocalDateTime updatedAt;

  public static List<CategoryMappingDto> convertMappings(List<CategoryMapping> mappings) {
    if (mappings == null) {
      return List.of();
    }
    return mappings.stream()
        .map(
            m ->
                CategoryMappingDto.builder()
                    .category(m.getCategory())
                    .weight(m.getWeight())
                    .build())
        .collect(Collectors.toList());
  }
}
