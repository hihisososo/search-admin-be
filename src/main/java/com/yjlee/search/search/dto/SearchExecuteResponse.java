package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "상품 검색 응답")
public class SearchExecuteResponse {

  @Schema(description = "검색 결과")
  private SearchHitsDto hits;

  @Schema(description = "집계 결과")
  private Map<String, List<AggregationBucketDto>> aggregations;

  @Schema(description = "메타 정보")
  private SearchMetaDto meta;
}
