package com.yjlee.search.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "가격 범위")
public class PriceRangeDto {

  @Min(value = 0, message = "최소 가격은 0 이상이어야 합니다")
  @Schema(description = "최소 가격", example = "500000")
  private Long from;

  @Min(value = 0, message = "최대 가격은 0 이상이어야 합니다")
  @Schema(description = "최대 가격", example = "2000000")
  private Long to;
}
