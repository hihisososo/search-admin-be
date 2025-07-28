package com.yjlee.search.evaluation.dto;

import java.util.List;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateCandidatesRequest {

  private List<Long> queryIds;
  private Boolean generateForAllQueries;
}
