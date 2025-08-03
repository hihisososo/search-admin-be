package com.yjlee.search.clicklog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "클릭 로그 요청")
public class ClickLogRequest {

  @NotBlank
  @Schema(description = "검색 키워드", example = "노트북")
  private String searchKeyword;

  @NotBlank
  @Schema(description = "클릭한 상품 ID", example = "PROD-12345")
  private String clickedProductId;

  @NotBlank
  @Schema(description = "검색 인덱스명", example = "products")
  private String indexName;
}
