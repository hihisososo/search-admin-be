package com.yjlee.search.dictionary.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카테고리 랭킹 사전 생성 요청")
public class CategoryRankingDictionaryCreateRequest {

  @NotBlank(message = "키워드는 필수입니다")
  @Size(max = 100, message = "키워드는 100자 이하여야 합니다")
  @Schema(description = "키워드", example = "아이폰", required = true)
  private String keyword;

  @NotEmpty(message = "카테고리 매핑은 최소 1개 이상 필요합니다")
  @Valid
  @Schema(description = "카테고리 매핑 목록", required = true)
  private List<CategoryMappingDto> categoryMappings;

  @Size(max = 500, message = "설명은 500자 이하여야 합니다")
  @Schema(description = "설명", example = "아이폰 관련 카테고리 부스팅")
  private String description;
}
