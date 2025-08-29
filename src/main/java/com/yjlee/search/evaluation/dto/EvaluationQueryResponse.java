package com.yjlee.search.evaluation.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationQueryResponse {
  private Long id;
  private String query;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}