package com.yjlee.search.evaluation.dto;

import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LLMEvaluationRequest {

  private List<Long> queryIds;
  private Boolean evaluateAllQueries;
}
