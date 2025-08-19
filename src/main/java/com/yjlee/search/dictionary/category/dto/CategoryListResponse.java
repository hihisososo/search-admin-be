package com.yjlee.search.dictionary.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "카테고리 목록 응답")
public class CategoryListResponse {

  @Schema(description = "전체 카테고리 개수", example = "10")
  private Integer totalCount;

  @Schema(description = "카테고리 목록", example = "[\"스마트폰\", \"애플\", \"전자제품\"]")
  private List<String> categories;
}
