package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "검색 모드")
public enum SearchMode {
  @Schema(description = "키워드 검색만 사용 (BM25)")
  KEYWORD_ONLY,

  @Schema(description = "다중 필드 벡터 검색 (가중치 적용)")
  VECTOR_MULTI_FIELD,

  @Schema(description = "하이브리드 검색 (커스텀 RRF)")
  HYBRID_RRF
}
