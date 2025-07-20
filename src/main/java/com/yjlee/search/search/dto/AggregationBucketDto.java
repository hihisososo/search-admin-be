package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "집계 버킷")
public class AggregationBucketDto {

  @Schema(description = "키", example = "Apple")
  private String key;

  @Schema(description = "문서 수", example = "45")
  private Long docCount;
}
