package com.yjlee.search.loggen.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkLogGenerationRequest {

  private LocalDate startDate;
  private LocalDate endDate;

  @Builder.Default private Integer logsPerDay = 20000;

  @Builder.Default private Double clickRate = 0.5; // 50% 클릭률
}
