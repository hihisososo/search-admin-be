package com.yjlee.search.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "연관검색어 계산 요청")
public class RelatedKeywordCalculationRequest {

  @Schema(description = "시작 일시", example = "2024-01-01T00:00:00")
  private LocalDateTime from;

  @Schema(description = "종료 일시", example = "2024-01-07T23:59:59")
  private LocalDateTime to;
}
