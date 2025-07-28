package com.yjlee.search.evaluation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateQueriesRequest {

  @NotNull(message = "생성할 쿼리 개수는 필수입니다")
  @Min(value = 1, message = "최소 1개 이상의 쿼리를 생성해야 합니다")
  @Max(value = 100, message = "최대 100개까지 생성할 수 있습니다")
  private Integer count;
}
