package com.yjlee.search.dictionary.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카테고리 매핑 정보")
public class CategoryMappingDto {

  @NotBlank(message = "카테고리는 필수입니다")
  @Schema(description = "카테고리명", example = "스마트폰", required = true)
  private String category;

  @Min(value = 1, message = "가중치는 1 이상이어야 합니다")
  @Builder.Default
  @Schema(description = "가중치 (기본값: 1000)", example = "1000")
  private Integer weight = 1000;
}
