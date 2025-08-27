package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "검색 모드")
public enum SearchMode {
  @Schema(description = "키워드 검색만 사용 (BM25)")
  KEYWORD_ONLY,

  @Schema(description = "벡터 검색만 사용 (KNN)")
  VECTOR_ONLY,

  @Schema(description = "하이브리드 검색 (커스텀 RRF)")
  HYBRID_RRF
}
