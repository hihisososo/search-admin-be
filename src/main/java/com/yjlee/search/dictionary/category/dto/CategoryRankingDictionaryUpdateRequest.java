package com.yjlee.search.dictionary.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카테고리 랭킹 사전 수정 요청")
public class CategoryRankingDictionaryUpdateRequest {

  @Size(max = 100, message = "키워드는 100자 이하여야 합니다")
  @Schema(description = "키워드", example = "아이폰")
  private String keyword;

  @Valid
  @Schema(description = "카테고리 매핑 목록 (전체 교체)")
  private List<CategoryMappingDto> categoryMappings;

  @Size(max = 500, message = "설명은 500자 이하여야 합니다")
  @Schema(description = "설명", example = "아이폰 관련 카테고리 부스팅")
  private String description;
}
