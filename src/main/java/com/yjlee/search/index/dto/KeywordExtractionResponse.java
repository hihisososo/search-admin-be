package com.yjlee.search.index.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "키워드 추출 결과")
public class KeywordExtractionResponse {

  @Schema(description = "원본 상품명", example = "아이폰 15 Pro 128GB 블루")
  private String productName;

  @Schema(description = "추출된 브랜드", example = "아이폰")
  private String brand;

  @Schema(description = "추출된 모델명 목록", example = "[\"iPhone\", \"15\", \"Pro\"]")
  private List<String> models;

  @Schema(description = "색상 키워드", example = "[\"블루\", \"파란색\"]")
  private List<String> colors;

  @Schema(description = "용량/크기 키워드", example = "[\"128GB\", \"128\"]")
  private List<String> specifications;

  @Schema(description = "특징 키워드", example = "[\"프로\", \"Pro\"]")
  private List<String> features;

  @Schema(description = "N-gram 키워드", example = "[\"아이폰\", \"아이폰 15\", \"15 Pro\"]")
  private List<String> ngrams;

  @Schema(description = "전체 키워드 (중복 제거)", example = "[\"아이폰\", \"15\", \"Pro\", \"128GB\", \"블루\"]")
  private List<String> allKeywords;
}
