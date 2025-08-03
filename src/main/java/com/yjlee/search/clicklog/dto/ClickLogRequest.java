package com.yjlee.search.clicklog.dto;

import com.yjlee.search.clicklog.model.ClickLogDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
  @Schema(description = "검색 세션 ID", example = "550e8400-e29b-41d4-a716-446655440000")
  private String searchSessionId;

  @NotBlank
  @Schema(description = "검색 키워드", example = "노트북")
  private String searchKeyword;

  @NotBlank
  @Schema(description = "클릭한 상품 ID", example = "PROD-12345")
  private String clickedProductId;

  @NotBlank
  @Schema(description = "클릭한 상품명", example = "삼성 갤럭시북3 프로")
  private String clickedProductName;

  @NotNull
  @Positive
  @Schema(description = "클릭 위치 (검색 결과 내 순위)", example = "3")
  private Integer clickPosition;

  @NotBlank
  @Schema(description = "검색 인덱스명", example = "products")
  private String indexName;

  @NotNull
  @Schema(description = "클릭 타입", example = "PRODUCT_DETAIL")
  private ClickLogDocument.ClickType clickType;

  @Schema(description = "체류 시간 (밀리초)", example = "5000")
  private Long dwellTimeMs;
}